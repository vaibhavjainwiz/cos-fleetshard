package org.bf2.cos.fleetshard.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Version(ManagedConnector.VERSION)
@Group(ManagedConnector.GROUP)
@ShortNames("mpsr")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ManagedProcessor extends CustomResource<ManagedProcessorSpec, ManagedProcessorStatus>
    implements Namespaced {

    public static final String DESIRED_STATE_READY = "ready";
    public static final String DESIRED_STATE_DELETED = "deleted";
    public static final String DESIRED_STATE_UNASSIGNED = "unassigned";
    public static final String DESIRED_STATE_STOPPED = "stopped";
    public static final String STATE_PROVISIONING = "provisioning";
    public static final String STATE_DE_PROVISIONING = "deprovisioning";

    @Override
    protected ManagedProcessorSpec initSpec() {
        return new ManagedProcessorSpec();
    }

    @Override
    protected ManagedProcessorStatus initStatus() {
        return new ManagedProcessorStatus();
    }
}
