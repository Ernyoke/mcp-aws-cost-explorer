package data;

import com.opencsv.bean.CsvBindByName;

public record Discount(@CsvBindByName(column = "service") String serviceName,
                       @CsvBindByName String discountType,
                       @CsvBindByName String currency,
                       @CsvBindByName double value) {
    public static class Builder {
        private String serviceName;
        private String discountType;
        private String currency;
        private double value;

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder discountType(String discountType) {
            this.discountType = discountType;
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

        public Discount build() {
            return new Discount(this.serviceName, this.discountType, this.currency, this.value);
        }
    }
}
