/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.matomo.model;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class MatomoRequestDetailsListConverter<T extends Collection<MatomoRequestDetails>> extends JsonSerializer<T> {

    private static final Logger log = LogManager.getLogger(MatomoRequestDetailsListConverter.class);
    // each request will be mapped to: ?parameter1=value1&parameter2=value2
    private final String requestTemplate = "?{0}";
    // each key-value will be mapped to: key=value
    private final String keyValueTemplate = "{0}={1}";

    @Override
    public void serialize(T requests, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
        gen.writeObject(
            requests.stream()
                    .map(this::mapRequest)
                    .collect(Collectors.toList())
        );
    }

    private String mapRequest(MatomoRequestDetails request) {
        return MessageFormat.format(requestTemplate, getRequestURL(request));
    }

    private String getRequestURL(MatomoRequestDetails request) {
        return request.parameters.entrySet()
                                 .stream()
                                 .filter(entry -> StringUtils.isNoneEmpty(entry.getValue(), entry.getKey()))
                                 .map(entry -> MessageFormat.format(keyValueTemplate, entry.getKey(), entry.getValue()))
                                 .collect(Collectors.joining("&"));
    }
}
