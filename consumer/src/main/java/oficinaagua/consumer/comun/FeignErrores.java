package oficinaagua.consumer.comun;

import feign.FeignException;
import java.util.Collection;
import java.util.Map;

/**
 * Lee datos de la respuesta de error de Feign.
 * Con Retry-After, Feign lanza RetryableException y el body y los headers quedan en la causa.
 */
public final class FeignErrores {

    private FeignErrores() {
    }

    /** El body de la respuesta de error, o null. */
    public static String cuerpo(FeignException e) {
        String cuerpo = e.contentUTF8();
        if ((cuerpo == null || cuerpo.isBlank()) && e.getCause() instanceof FeignException causa) {
            cuerpo = causa.contentUTF8();
        }
        return cuerpo;
    }

    /** HU-48: segundos que pide el destino en Retry-After, o null si no lo mando. */
    public static Long retryAfterSegundos(FeignException e) {
        String valor = cabecera(e, "Retry-After");
        if (valor == null && e.getCause() instanceof FeignException causa) {
            valor = cabecera(causa, "Retry-After");
        }
        if (valor == null) {
            return null;
        }
        try {
            return Long.parseLong(valor.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String cabecera(FeignException e, String nombre) {
        Map<String, Collection<String>> headers = e.responseHeaders();
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, Collection<String>> h : headers.entrySet()) {
            if (h.getKey().equalsIgnoreCase(nombre) && !h.getValue().isEmpty()) {
                return h.getValue().iterator().next();
            }
        }
        return null;
    }
}