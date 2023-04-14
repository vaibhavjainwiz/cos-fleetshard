package org.bf2.cos.fleetshard.operator.camel.processor;

import java.util.Collections;

import javax.inject.Inject;

import org.bf2.cos.fleetshard.api.ManagedProcessor;
import org.bf2.cos.fleetshard.api.ManagedProcessorStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import static org.bf2.cos.fleetshard.api.ManagedProcessor.STATE_PROVISIONING;

@ControllerConfiguration(
    name = "processor",
    generationAwareEventProcessing = false)
public class ProcessorController implements Reconciler<ManagedProcessor> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorController.class);

    @Inject
    ProcessorInitializationHandler processorInitializationHandler;

    @Inject
    ProcessorAugmentationHandler processorAugmentationHandler;

    @Override
    public UpdateControl<ManagedProcessor> reconcile(ManagedProcessor processor, Context<ManagedProcessor> context)
        throws Exception {

        LOGGER.info("Reconcile {}:{}:{}@{} (phase={})",
            processor.getApiVersion(),
            processor.getKind(),
            processor.getMetadata().getName(),
            processor.getMetadata().getNamespace(),
            processor.getStatus().getPhase());

        if (processor.getStatus().getPhase() == null) {
            processor.getStatus().setPhase(ManagedProcessorStatus.PhaseType.Initialization);
            processor.getStatus().getProcessorStatus().setPhase(STATE_PROVISIONING);
            processor.getStatus().getProcessorStatus().setConditions(Collections.emptyList());
        }

        switch (processor.getStatus().getPhase()) {
            case Initialization:
                return handleInitialization(processor);
            case Augmentation:
                return handleAugmentation(processor);
            /*
             * case Monitor:
             * return validate(resource, this::handleMonitor);
             * case Deleting:
             * return handleDeleting(resource);
             * case Deleted:
             * return validate(resource, this::handleDeleted);
             * case Stopping:
             * return handleStopping(resource);
             * case Stopped:
             * return validate(resource, this::handleStopped);
             * case Transferring:
             * return handleTransferring(resource);
             * case Transferred:
             * return handleTransferred(resource);
             * case Error:
             * return validate(resource, this::handleError);
             */
            default:
                return UpdateControl.updateStatus(processor);
        }
    }

    // **************************************************
    //
    // Handlers
    //
    // **************************************************

    private UpdateControl<ManagedProcessor> handleInitialization(ManagedProcessor processor) {
        processorInitializationHandler.handleInitialization(processor);
        return UpdateControl.updateStatus(processor);
    }

    private UpdateControl<ManagedProcessor> handleAugmentation(ManagedProcessor processor) {
        return processorAugmentationHandler.handleAugmentation(processor);
    }
}
