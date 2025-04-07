package data;

import com.opencsv.bean.CsvBindByName;

public record UsagePerLinkedAccountAndService(@CsvBindByName(column = "AWS Account Number") String linkedAccount,
                                              @CsvBindByName(column = "Service") String serviceName,
                                              @CsvBindByName String currency,
                                              @CsvBindByName double value) implements Usage {
    public static class Builder {
        private String linkedAccount;
        private String serviceName;
        private String currency;
        private double value;


        public Builder linkedAccount(String linkedAccount) {
            this.linkedAccount = linkedAccount;
            return this;
        }

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
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

        public UsagePerLinkedAccountAndService build() {
            return new UsagePerLinkedAccountAndService(this.linkedAccount, this.serviceName, this.currency, this.value);
        }
    }
}
