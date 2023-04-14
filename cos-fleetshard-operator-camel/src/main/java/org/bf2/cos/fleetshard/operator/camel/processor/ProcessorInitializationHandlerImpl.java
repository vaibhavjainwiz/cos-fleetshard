package org.bf2.cos.fleetshard.operator.camel.processor;

import java.util.Collections;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.bf2.cos.fleetshard.api.ManagedConnectorOperator;
import org.bf2.cos.fleetshard.api.ManagedProcessor;
import org.bf2.cos.fleetshard.api.ManagedProcessorStatus;

import static org.bf2.cos.fleetshard.api.ManagedProcessor.DESIRED_STATE_DELETED;
import static org.bf2.cos.fleetshard.api.ManagedProcessor.DESIRED_STATE_READY;
import static org.bf2.cos.fleetshard.api.ManagedProcessor.DESIRED_STATE_STOPPED;
import static org.bf2.cos.fleetshard.api.ManagedProcessor.DESIRED_STATE_UNASSIGNED;
import static org.bf2.cos.fleetshard.api.ManagedProcessor.STATE_DE_PROVISIONING;
import static org.bf2.cos.fleetshard.api.ManagedProcessor.STATE_PROVISIONING;
import static org.bf2.cos.fleetshard.operator.camel.processor.ProcessorConditions.setCondition;

@ApplicationScoped
public class ProcessorInitializationHandlerImpl implements ProcessorInitializationHandler {

    @Inject
    ManagedConnectorOperator managedConnectorOperator;

    @Override
    public void handleInitialization(ManagedProcessor processor) {
        ProcessorConditions.clearConditions(processor);

        setCondition(processor, ProcessorConditions.Type.Initialization, true);
        setCondition(processor, ProcessorConditions.Type.Ready, false, "Initialization");

        switch (processor.getSpec().getDesiredState()) {
            case DESIRED_STATE_UNASSIGNED:
            case DESIRED_STATE_DELETED: {
                setCondition(
                    processor,
                    ProcessorConditions.Type.Deleting,
                    ProcessorConditions.Status.True,
                    "Deleting");

                processor.getStatus().setPhase(ManagedProcessorStatus.PhaseType.Deleting);
                processor.getStatus().getProcessorStatus().setPhase(STATE_DE_PROVISIONING);
                processor.getStatus().getProcessorStatus().setConditions(Collections.emptyList());
                break;
            }
            case DESIRED_STATE_STOPPED: {
                setCondition(
                    processor,
                    ProcessorConditions.Type.Stopping,
                    ProcessorConditions.Status.True,
                    "Stopping");

                processor.getStatus().setPhase(ManagedProcessorStatus.PhaseType.Stopping);
                processor.getStatus().getProcessorStatus().setPhase(STATE_DE_PROVISIONING);
                processor.getStatus().getProcessorStatus().setConditions(Collections.emptyList());
                break;
            }
            case DESIRED_STATE_READY: {
                setCondition(processor, ProcessorConditions.Type.Augmentation, true);
                setCondition(processor, ProcessorConditions.Type.Ready, false);

                processor.getStatus().setPhase(ManagedProcessorStatus.PhaseType.Augmentation);
                processor.getStatus().getProcessorStatus().setPhase(STATE_PROVISIONING);
                processor.getStatus().getProcessorStatus().setConditions(Collections.emptyList());

                break;
            }
            default:
                throw new IllegalStateException(
                    "Unknown desired state: " + processor.getSpec().getDesiredState());
        }
    }
}
