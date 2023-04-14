package org.bf2.cos.fleetshard.operator.camel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntegrationSpec {

    @JsonProperty("flows")
    private ArrayNode flows;

    @JsonProperty("traits")
    private ObjectNode traits;

    public ArrayNode getFlows() {
        return flows;
    }

    public void setFlows(ArrayNode flows) {
        this.flows = flows;
    }

    public ObjectNode getTraits() {
        return traits;
    }

    public void setTraits(ObjectNode traits) {
        this.traits = traits;
    }
}
