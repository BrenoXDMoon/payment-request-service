package br.com.breno.itaucorp.paymentrequestservice.domain.model;

import java.util.EnumSet;
import java.util.Set;

public enum PaymentStatus {

    CREATED {
        @Override
        public Set<PaymentStatus> allowedNextStates() {
            return EnumSet.of(PROCESSING);
        }
    },
    PROCESSING {
        @Override
        public Set<PaymentStatus> allowedNextStates() {
            return EnumSet.of(COMPLETED, REJECTED, FAILED);
        }
    },
    COMPLETED {
        @Override
        public Set<PaymentStatus> allowedNextStates() {
            return EnumSet.noneOf(PaymentStatus.class);
        }
    },
    REJECTED {
        @Override
        public Set<PaymentStatus> allowedNextStates() {
            return EnumSet.noneOf(PaymentStatus.class);
        }
    },
    FAILED {
        @Override
        public Set<PaymentStatus> allowedNextStates() {
            return EnumSet.noneOf(PaymentStatus.class);
        }
    };

    public abstract Set<PaymentStatus> allowedNextStates();

    public boolean canTransitionTo(PaymentStatus target) {
        return allowedNextStates().contains(target);
    }
}
