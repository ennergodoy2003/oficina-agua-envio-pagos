"""Pruebas de la Lambda HU-49 sin base de datos: la conexion es falsa y solo anota los INSERT."""
import pymysql

import handler


class CursorFalso:
    def __init__(self, conexion):
        self.conexion = conexion

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def execute(self, sql, parametros):
        error = self.conexion.errores.get(parametros)
        if error:
            raise pymysql.err.IntegrityError(error, "error simulado")
        self.conexion.inserts.append(parametros)


class ConexionFalsa:
    def __init__(self, errores=None):
        self.errores = errores or {}  # {(pago_id, destino): codigo_de_error}
        self.inserts = []
        self.cerrada = False

    def cursor(self):
        return CursorFalso(self)

    def commit(self):
        pass

    def rollback(self):
        pass

    def close(self):
        self.cerrada = True


def registro(evento, numero, destino, resultado):
    return {"eventName": evento, "dynamodb": {"NewImage": {
        "numeroDocumento": {"S": numero},
        "destino": {"S": destino},
        "resultado": {"S": resultado}}}}


def ejecutar(registros, conexion):
    return handler.handler({"Records": registros}, None, conectar=lambda: conexion)


def test_pago_id_sale_del_numero_de_documento():
    assert handler.pago_id_de("EQA-000003") == 3
    assert handler.pago_id_de("EQA-000123") == 123
    assert handler.pago_id_de("basura") is None


def test_exitoso_marca_el_destino_del_registro():
    conexion = ConexionFalsa()

    resumen = ejecutar([registro("INSERT", "EQA-000003", "BANCO", "EXITOSO"),
                        registro("INSERT", "EQA-000004", "ERP", "EXITOSO")], conexion)

    assert conexion.inserts == [(3, "BANCO"), (4, "ERP")]
    assert resumen["MARCADO"] == 2
    assert conexion.cerrada


def test_fallido_no_marca_nada_ni_abre_conexion():
    conexion = ConexionFalsa()

    resumen = ejecutar([registro("INSERT", "EQA-000003", "BANCO", "FALLIDO")], conexion)

    assert conexion.inserts == []
    assert resumen["MARCADO"] == 0
    assert not conexion.cerrada  # nunca se abrio


def test_borrados_se_ignoran():
    conexion = ConexionFalsa()

    ejecutar([registro("REMOVE", "EQA-000003", "BANCO", "EXITOSO")], conexion)

    assert conexion.inserts == []


def test_evento_repetido_no_falla():
    conexion = ConexionFalsa(errores={(3, "BANCO"): handler.ERROR_DUPLICADO})

    resumen = ejecutar([registro("INSERT", "EQA-000003", "BANCO", "EXITOSO")], conexion)

    assert resumen["YA_MARCADO"] == 1


def test_pago_inexistente_se_registra_y_sigue_con_los_demas():
    conexion = ConexionFalsa(errores={(999, "BANCO"): handler.ERROR_LLAVE_FORANEA})

    resumen = ejecutar([registro("INSERT", "EQA-000999", "BANCO", "EXITOSO"),
                        registro("INSERT", "EQA-000001", "BANCO", "EXITOSO")], conexion)

    assert resumen["PAGO_NO_EXISTE"] == 1
    assert resumen["MARCADO"] == 1
    assert conexion.inserts == [(1, "BANCO")]


def test_datos_invalidos_se_ignoran():
    conexion = ConexionFalsa()

    ejecutar([registro("INSERT", "basura", "BANCO", "EXITOSO"),
              registro("INSERT", "EQA-000001", "OTRO", "EXITOSO")], conexion)

    assert conexion.inserts == []