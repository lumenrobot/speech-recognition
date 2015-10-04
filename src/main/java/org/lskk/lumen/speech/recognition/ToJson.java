package org.lskk.lumen.speech.recognition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.base.Throwables;
import org.apache.camel.Body;
import org.springframework.stereotype.Service;

import java.util.function.Function;

/**
 * Created by ceefour on 19/01/15.
 */
@Service
public class ToJson implements Function<Object, String> {

    protected ObjectMapper mapper;

    public ToJson() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JodaModule());
        mapper.registerModule(new GuavaModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public String apply(@Body Object o) {
        try {
            return o != null ? mapper.writeValueAsString(o) : null;
        } catch (JsonProcessingException e) {
            Throwables.propagate(e);
            return null;
        }
    }

    public ObjectMapper getMapper() {
        return mapper;
    }
}
