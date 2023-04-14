package org.bf2.cos.fleetshard.operator.util;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.bf2.cos.fleetshard.api.Conditions;

import io.fabric8.kubernetes.api.model.Condition;

public final class ConditionUtil {

    private ConditionUtil() {

    }

    public static void clearConditions(List<Condition> conditions) {
        if (conditions != null) {
            conditions.clear();
        }
    }

    public static boolean setCondition(List<Condition> conditions, String type, String status, String reason, String message) {
        Condition condition = new Condition();
        condition.setType(type);
        condition.setStatus(status);
        condition.setReason(reason);
        condition.setMessage(message);
        condition.setLastTransitionTime(Conditions.now());

        return setCondition(conditions, condition);
    }

    public static boolean setCondition(List<Condition> conditions, Condition condition) {
        for (int i = 0; i < conditions.size(); i++) {
            final Condition current = conditions.get(i);

            if (Objects.equals(current.getType(), condition.getType())) {
                boolean update = !Objects.equals(condition.getStatus(), current.getStatus())
                    || !Objects.equals(condition.getReason(), current.getReason())
                    || !Objects.equals(condition.getMessage(), current.getMessage());

                if (update) {
                    conditions.set(i, condition);
                }

                conditions.sort(Comparator.comparing(Condition::getLastTransitionTime));

                return update;
            }
        }

        conditions.add(condition);
        conditions.sort(Comparator.comparing(Condition::getLastTransitionTime));

        return true;
    }

    public static boolean hasCondition(List<Condition> conditions, String type) {
        if (conditions == null) {
            return false;
        }

        return conditions.stream().anyMatch(
            c -> Objects.equals(c.getType(), type));
    }

    public static boolean hasCondition(List<Condition> conditions, String type, String status) {
        if (conditions == null) {
            return false;
        }

        return conditions.stream().anyMatch(
            c -> Objects.equals(c.getType(), type)
                && Objects.equals(c.getStatus(), status));
    }

    public static boolean hasCondition(List<Condition> conditions, String type, String status, String reason) {
        if (conditions == null) {
            return false;
        }

        return conditions.stream().anyMatch(c -> Objects.equals(c.getType(), type)
            && Objects.equals(c.getStatus(), status)
            && Objects.equals(c.getReason(), reason));
    }

    public static boolean hasCondition(List<Condition> conditions, String type, String status, String reason, String message) {
        if (conditions == null) {
            return false;
        }

        return conditions.stream().anyMatch(c -> Objects.equals(c.getType(), type)
            && Objects.equals(c.getStatus(), status)
            && Objects.equals(c.getReason(), reason)
            && Objects.equals(c.getMessage(), message));
    }
}
