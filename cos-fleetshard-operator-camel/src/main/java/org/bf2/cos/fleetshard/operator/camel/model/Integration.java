package org.bf2.cos.fleetshard.operator.camel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import static org.bf2.cos.fleetshard.operator.camel.model.Integration.RESOURCE_GROUP;
import static org.bf2.cos.fleetshard.operator.camel.model.Integration.RESOURCE_VERSION;

@ToString
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Version(RESOURCE_VERSION)
@Group(RESOURCE_GROUP)
public class Integration extends CustomResource<IntegrationSpec, IntegrationStatus> implements Namespaced {

    public static final String RESOURCE_GROUP = "camel.apache.org";
    public static final String RESOURCE_VERSION = "v1";

    @Override
    protected IntegrationSpec initSpec() {
        return new IntegrationSpec();
    }

    @Override
    protected IntegrationStatus initStatus() {
        return new IntegrationStatus();
    }
}
