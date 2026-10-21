"""
Solo para LOCAL: imita el event source mapping de AWS.
Lee el stream de DynamoDB Local y llama a handler.handler con los registros, en el mismo formato.
En AWS este archivo no se usa: el stream dispara la Lambda solo.
"""
import logging
import os
import time

import boto3

import handler

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("poller")

ENDPOINT = os.environ.get("DYNAMODB_ENDPOINT", "http://dynamodb:8000")
TABLA = os.environ.get("DYNAMODB_TABLE", "pagos-envio-logs")
REGION = os.environ.get("AWS_DEFAULT_REGION", "us-east-1")


def clientes():
    opciones = dict(endpoint_url=ENDPOINT, region_name=REGION,
                    aws_access_key_id="local", aws_secret_access_key="local")
    return boto3.client("dynamodb", **opciones), boto3.client("dynamodbstreams", **opciones)


def iteradores(dynamo, streams):
    """Un iterador por shard, desde el inicio (TRIM_HORIZON). Reprocesar es seguro: la Lambda es idempotente."""
    arn = dynamo.describe_table(TableName=TABLA)["Table"]["LatestStreamArn"]
    shards = streams.describe_stream(StreamArn=arn)["StreamDescription"]["Shards"]
    resultado = {}
    for shard in shards:
        resultado[shard["ShardId"]] = streams.get_shard_iterator(
            StreamArn=arn, ShardId=shard["ShardId"],
            ShardIteratorType="TRIM_HORIZON")["ShardIterator"]
    log.info("Escuchando el stream de %s (%d shard(s))", TABLA, len(resultado))
    return resultado


def main():
    dynamo, streams = clientes()
    pendientes = {}
    while True:
        try:
            if not pendientes:
                pendientes = iteradores(dynamo, streams)
            for shard_id, iterador in list(pendientes.items()):
                respuesta = streams.get_records(ShardIterator=iterador, Limit=100)
                registros = respuesta.get("Records", [])
                if registros:
                    resumen = handler.handler({"Records": registros}, None)
                    log.info("%d registro(s) del stream -> %s", len(registros), resumen)
                siguiente = respuesta.get("NextShardIterator")
                if siguiente:
                    pendientes[shard_id] = siguiente
                else:
                    del pendientes[shard_id]  # shard cerrado
        except Exception as e:
            log.warning("Error leyendo el stream o escribiendo en MySQL (%s). Reintento en 5 s", e)
            pendientes = {}
            time.sleep(5)
        time.sleep(1)


if __name__ == "__main__":
    main()