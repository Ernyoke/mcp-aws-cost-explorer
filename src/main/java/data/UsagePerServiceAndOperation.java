package data;

import com.opencsv.bean.CsvBindByName;

public record UsagePerServiceAndOperation(@CsvBindByName(column = "service") String serviceName,
                                          @CsvBindByName String operation,
                                          @CsvBindByName String currency,
                                          @CsvBindByName double value) implements Usage {
    public static class Builder {
        private String serviceName;
        private String operation;
        private String currency;
        private double value;

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder operation(String discountType) {
            this.operation = discountType;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder value(double value) {
            this.value = value;
            return this;
        }

        public UsagePerServiceAndOperation build() {
            return new UsagePerServiceAndOperation(this.serviceName, this.operation, this.currency, this.value);
        }
    }
}
