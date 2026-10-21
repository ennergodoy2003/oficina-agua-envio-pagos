"""
HU-49: Lambda que marca como ENVIADO un pago en un destino.

Se activa con el stream de DynamoDB Logs (pagos-envio-logs). Por cada INSERT con
resultado EXITOSO hace INSERT en keko_copia.pagos_envio (pago_id, destino).
En pagos_envio, la fila existe = ENVIADO; no existe = PENDIENTE (ver v_pagos_envio).
Los FALLIDO no tocan nada: el pago sigue PENDIENTE y el producer lo vuelve a publicar.
"""
import logging
import os
import re

import pymysql

log = logging.getLogger()
log.setLevel(logging.INFO)

# EQA-000003 -> 3 (inverso de concat('EQA-', lpad(id, 6, '0')) en la vista)
PATRON_DOCUMENTO = re.compile(r"^[A-Z]+-(\d+)$")
DESTINOS = {"BANCO", "ERP"}

ERROR_DUPLICADO = 1062      # la fila (pago_id, destino) ya existe
ERROR_LLAVE_FORANEA = 1452  # el pago no existe en la tabla pagos


def pago_id_de(numero_documento):
    """EQA-000003 -> 3. None si no tiene el formato esperado."""
    coincidencia = PATRON_DOCUMENTO.match(numero_documento or "")
    return int(coincidencia.group(1)) if coincidencia else None


def marcas_de(event):
    """De los registros del stream, devuelve [(pago_id, destino)] de los EXITOSO insertados."""
    marcas = []
    for registro in event.get("Records", []):
        if registro.get("eventName") != "INSERT":
            continue  # MODIFY o REMOVE (por ejemplo, al limpiar la tabla) no marcan nada
        imagen = registro.get("dynamodb", {}).get("NewImage", {})
        if imagen.get("resultado", {}).get("S") != "EXITOSO":
            continue  # un FALLIDO deja el pago PENDIENTE
        numero = imagen.get("numeroDocumento", {}).get("S")
        destino = imagen.get("destino", {}).get("S")
        pago_id = pago_id_de(numero)
        if pago_id is None or destino not in DESTINOS:
            log.warning("Registro EXITOSO con datos invalidos, se ignora: %s / %s", numero, destino)
            continue
        marcas.append((pago_id, destino))
    return marcas


def marcar(conexion, pago_id, destino):
    """INSERT de la marca. Devuelve MARCADO, YA_MARCADO o PAGO_NO_EXISTE."""
    try:
        with conexion.cursor() as cursor:
            cursor.execute(
                "INSERT INTO pagos_envio (pago_id, destino) VALUES (%s, %s)",
                (pago_id, destino))
        conexion.commit()
        return "MARCADO"
    except pymysql.err.IntegrityError as e:
        conexion.rollback()
        if e.args[0] == ERROR_DUPLICADO:
            return "YA_MARCADO"  # evento repetido: no pasa nada
        if e.args[0] == ERROR_LLAVE_FORANEA:
            log.error("El pago %s no existe en la tabla pagos", pago_id)
            return "PAGO_NO_EXISTE"
        raise


def conectar():
    """Conexion real. Usuario lambda_flags: solo tiene INSERT en pagos_envio."""
    return pymysql.connect(
        host=os.environ["DB_HOST"],
        port=int(os.environ.get("DB_PORT", "3306")),
        user=os.environ["DB_USER"],
        password=os.environ["DB_PASSWORD"],
        database=os.environ["DB_NAME"],
        connect_timeout=5)


def handler(event, context, conectar=conectar):
    """Punto de entrada de la Lambda. En las pruebas se le pasa una conexion falsa."""
    resumen = {"MARCADO": 0, "YA_MARCADO": 0, "PAGO_NO_EXISTE": 0}
    marcas = marcas_de(event)
    if not marcas:
        return resumen  # ningun EXITOSO: ni siquiera se abre conexion

    conexion = conectar()
    try:
        for pago_id, destino in marcas:
            resultado = marcar(conexion, pago_id, destino)
            resumen[resultado] += 1
            log.info("Pago %s en %s: %s", pago_id, destino, resultado)
    finally:
        conexion.close()
    return resumen