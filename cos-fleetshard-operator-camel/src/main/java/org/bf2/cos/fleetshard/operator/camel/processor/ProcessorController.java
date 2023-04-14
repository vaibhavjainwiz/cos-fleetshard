package org.bf2.cos.fleetshard.operator.camel.processor;

import javax.inject.Inject;

import org.bf2.cos.fleetshard.api.ManagedProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(
    name = "processor",
    generationAwareEventProcessing = false)
public class ProcessorController implements Reconciler<ManagedProcessor> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorController.class);

    @Inject
    ProcessorSecretValidator processorSecretValidator;

    @Inject
    IntegrationSecretReconciler integrationSecretReconciler;

    @Inject
    IntegrationReconciler integrationReconciler;

    @Override
    public UpdateControl<ManagedProcessor> reconcile(ManagedProcessor processor, Context<ManagedProcessor> context)
        throws Exception {

        LOGGER.info("Reconcile {}:{}:{}@{} (phase={})",
            processor.getApiVersion(),
            processor.getKind(),
            processor.getMetadata().getName(),
            processor.getMetadata().getNamespace(),
            processor.getStatus().getPhase());

        // validate processor secret
        var updateControl = processorSecretValidator.validateProcessorSecret(processor);
        if (updateControl != null) {
            return updateControl;
        }

        // create secret
        var deltaProcessed = integrationSecretReconciler.reconcile(processor);
        if (deltaProcessed) {
            return UpdateControl.updateStatus(processor);
        }

        // create integration
        deltaProcessed = integrationReconciler.reconcile(processor);
        if (deltaProcessed) {
            return UpdateControl.updateStatus(processor);
        }

        return UpdateControl.noUpdate();
    }
}
