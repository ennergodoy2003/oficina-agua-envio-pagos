package oficinaagua.producer.pago;

public enum Destino {
    BANCO("banco"),
    ERP("erp");

    private final String routingKey;

    Destino(String routingKey) {
        this.routingKey = routingKey;
    }

    /** Etiqueta del sobre: debe coincidir con los bindings de rabbitmq/definitions.json. */
    public String routingKey() {
        return routingKey;
    }
}