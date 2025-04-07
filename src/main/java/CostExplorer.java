import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import data.*;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class CostExplorer {
    Logger logger = Logger.getLogger(CostExplorer.class);

    private final Expression discountsFilter = Expression.builder()
            .dimensions(DimensionValues.builder()
                    .key(Dimension.RECORD_TYPE)
                    .values(List.of("Refund", "Credit", "DiscountedUsage", "Discount",
                            "BundledDiscount ", "SavingsPlanCoveredUsage", "SavingsPlanNegation"))
                    .build())
            .build();

    private final Expression excludeDiscountsFilter = Expression.builder()
            .not(discountsFilter)
            .build();

    private final CostExplorerClient costExplorerClient;

    @Inject
    public CostExplorer(CostExplorerClient costExplorerClient) {
        this.costExplorerClient = costExplorerClient;
    }

    @Tool(description = "Return all the costs by services and operations in all the regions for a given period.")
    public String getCostPerServiceAndOperation(
            @ToolArg(description = "Start date in format of yyyy-MM-dd") String startDate,
            @ToolArg(description = "End date in format of yyyy-MM-dd") String endDate,
            @ToolArg(description = "AWS regions where the costs should be retrieved. In case we want to get the costs for resources in all regions, we should provide an empty list.") List<String> regions
    ) {
        Utils.validateStartEndEndDate(startDate, endDate);

        List<Expression> filterExpressionList = new ArrayList<>();

        if (!regions.isEmpty()) {
            filterExpressionList.add(Expression.builder()
                    .dimensions(DimensionValues.builder()
                            .key(Dimension.REGION)
                            .values(regions)
                            .build())
                    .build());
        }

        filterExpressionList.add(excludeDiscountsFilter);

        Expression filterExpression = null;

        if (filterExpressionList.size() == 1) {
            filterExpression = filterExpressionList.getFirst();
        }

        if (filterExpressionList.size() > 1) {
            filterExpression = Expression.builder().and(filterExpressionList).build();
        }

        List<UsagePerServiceAndOperation> usagePerServiceAndOperations = new ArrayList<>();
        Map<String, Double> totalCostPerCurrency = new HashMap<>();

        String nextPageToken = null;

        do {
            GetCostAndUsageRequest request = GetCostAndUsageRequest.builder()
                    .timePeriod(DateInterval.builder()
                            .start(startDate)
                            .end(endDate)
                            .build())
                    .granularity(Granularity.MONTHLY)
                    .metrics(List.of("UnblendedCost"))
                    .filter(filterExpression)
                    .groupBy(GroupDefinition.builder()
                                    .type(GroupDefinitionType.DIMENSION)
                                    .key("SERVICE")
                                    .build(),
                            GroupDefinition.builder()
                                    .type(GroupDefinitionType.DIMENSION)
                                    .key("OPERATION")
                                    .build()
                    )
                    .nextPageToken(nextPageToken)
                    .build();

            try {
                GetCostAndUsageResponse response = costExplorerClient.getCostAndUsage(request);
                for (ResultByTime result : response.resultsByTime()) {
                    for (Group group : result.groups()) {
                        double cost = Double.parseDouble(group.metrics().get("UnblendedCost").amount());

                        String serviceName = group.keys().getFirst();
                        String operation = group.keys().getLast();
                        String currency = group.metrics().get("UnblendedCost").unit();
                        usagePerServiceAndOperations.add(new UsagePerServiceAndOperation.Builder()
                                .serviceName(serviceName)
                                .operation(operation)
                                .currency(currency)
                                .value(cost)
                                .build());

                        totalCostPerCurrency.compute(currency, (key, value) -> value == null ? 0 : value + cost);
                    }
                }
                nextPageToken = response.nextPageToken();
            } catch (CostExplorerException e) {
                this.logger.error(e);
                throw new ToolCallException("AWS API error: " + e.getMessage());
            }

        } while (nextPageToken != null);

        usagePerServiceAndOperations.sort((a, b) -> Double.compare(b.value(), a.value()));

        try {
            UsageReport usageReport = new UsageReport(usagePerServiceAndOperations, totalCostPerCurrency);
            return usageReport.generate();
        } catch (CsvRequiredFieldEmptyException | CsvDataTypeMismatchException e) {
            this.logger.error(e);
            throw new ToolCallException("Error generating report:" + e.getMessage());
        }
    }

    @Tool(description = "Return all the costs by linked account and service in selected regions for a given period.")
    public String getCostPerLinkedAccount(
            @ToolArg(description = "Start date in format of yyyy-MM-dd") String startDate,
            @ToolArg(description = "End date in format of yyyy-MM-dd") String endDate,
            @ToolArg(description = "AWS regions where the costs should be retrieved. In case we want to get the costs for resources in all regions, we should provide an empty list.") List<String> regions
    ) {
        Utils.validateStartEndEndDate(startDate, endDate);

        List<Expression> filterExpressionList = new ArrayList<>();

        if (!regions.isEmpty()) {
            filterExpressionList.add(Expression.builder()
                    .dimensions(DimensionValues.builder()
                            .key(Dimension.REGION)
                            .values(regions)
                            .build())
                    .build());
        }

        filterExpressionList.add(excludeDiscountsFilter);

        Expression filterExpression = null;

        if (filterExpressionList.size() == 1) {
            filterExpression = filterExpressionList.getFirst();
        }

        if (filterExpressionList.size() > 1) {
            filterExpression = Expression.builder().and(filterExpressionList).build();
        }

        List<UsagePerLinkedAccountAndService> usagePerLinkedAccountAndServices = new ArrayList<>();
        Map<String, Double> totalCostPerCurrency = new HashMap<>();

        String nextPageToken = null;

        do {
            GetCostAndUsageRequest request = GetCostAndUsageRequest.builder()
                    .timePeriod(DateInterval.builder()
                            .start(startDate)
                            .end(endDate)
                            .build())
                    .granularity(Granularity.MONTHLY)
                    .metrics(List.of("UnblendedCost"))
                    .filter(filterExpression)
                    .groupBy(GroupDefinition.builder()
                                    .type(GroupDefinitionType.DIMENSION)
                                    .key("LINKED_ACCOUNT")
                                    .build(),
                            GroupDefinition.builder()
                                    .type(GroupDefinitionType.DIMENSION)
                                    .key("SERVICE")
                                    .build()
                    )
                    .nextPageToken(nextPageToken)
                    .build();

            try {
                GetCostAndUsageResponse response = costExplorerClient.getCostAndUsage(request);
                for (ResultByTime result : response.resultsByTime()) {
                    for (Group group : result.groups()) {
                        double cost = Double.parseDouble(group.metrics().get("UnblendedCost").amount());

                        String linkedAccount = group.keys().getFirst();
                        String serviceName = group.keys().getLast();
                        String currency = group.metrics().get("UnblendedCost").unit();
                        usagePerLinkedAccountAndServices.add(new UsagePerLinkedAccountAndService.Builder()
                                .serviceName(serviceName)
                                .linkedAccount(linkedAccount)
                                .currency(currency)
                                .value(cost)
                                .build());

                        totalCostPerCurrency.compute(currency, (key, value) -> value == null ? 0 : value + cost);
                    }
                }
                nextPageToken = response.nextPageToken();
            } catch (CostExplorerException e) {
                this.logger.error(e);
                throw new ToolCallException("AWS API error: " + e.getMessage());
            }

        } while (nextPageToken != null);

        usagePerLinkedAccountAndServices.sort((a, b) -> Double.compare(b.value(), a.value()));

        try {
            UsageReport usageReport = new UsageReport(usagePerLinkedAccountAndServices, totalCostPerCurrency);
            return usageReport.generate();
        } catch (CsvRequiredFieldEmptyException | CsvDataTypeMismatchException e) {
            this.logger.error(e);
            throw new ToolCallException("Error generating report:" + e.getMessage());
        }
    }

    @Tool(description = "Return all the discounts by services and operations in all the regions for a given period.")
    public String getDiscounts(
            @ToolArg(description = "Start date in format of yyyy-MM-dd") String startDate,
            @ToolArg(description = "End date in format of yyyy-MM-dd") String endDate,
            @ToolArg(description = "AWS regions where the discounts should be retrieved. In case we want to get the " +
                    "discounts for resources in all regions, we should provide an empty list.") List<String> regions
    ) {
        Utils.validateStartEndEndDate(startDate, endDate);

        List<Expression> filterExpressionList = new ArrayList<>();

        if (!regions.isEmpty()) {
            filterExpressionList.add(Expression.builder()
                    .dimensions(DimensionValues.builder()
                            .key(Dimension.REGION)
                            .values(regions)
                            .build())
                    .build());
        }

        filterExpressionList.add(discountsFilter);

        Expression filterExpression = null;

        if (filterExpressionList.size() == 1) {
            filterExpression = filterExpressionList.getFirst();
        }

        if (filterExpressionList.size() > 1) {
            filterExpression = Expression.builder().and(filterExpressionList).build();
        }

        List<Discount> discounts = new ArrayList<>();

        Map<String, Double> totalDiscountPerCurrency = new HashMap<>();

        String nextPageToken = null;

        do {
            GetCostAndUsageRequest request = GetCostAndUsageRequest.builder()
                    .timePeriod(DateInterval.builder()
                            .start(startDate)
                            .end(endDate)
                            .build())
                    .granularity(Granularity.MONTHLY)
                    .metrics(List.of("UnblendedCost"))
                    .filter(filterExpression)
                    .groupBy(GroupDefinition.builder()
                                    .type(GroupDefinitionType.DIMENSION)
                                    .key("SERVICE")
                                    .build(),
                            GroupDefinition.builder()
                                    .type(GroupDefinitionType.DIMENSION)
                                    .key("RECORD_TYPE")
                                    .build()
                    )
                    .nextPageToken(nextPageToken)
                    .build();

            try {
                GetCostAndUsageResponse response = costExplorerClient.getCostAndUsage(request);

                for (ResultByTime result : response.resultsByTime()) {
                    for (Group group : result.groups()) {
                        double discount = Double.parseDouble(group.metrics().get("UnblendedCost").amount());

                        String serviceName = group.keys().getFirst();
                        String discountType = group.keys().getLast();
                        String currency = group.metrics().get("UnblendedCost").unit();
                        discounts.add(new Discount.Builder()
                                .serviceName(serviceName)
                                .discountType(discountType)
                                .currency(currency).value(discount)
                                .build());
                        totalDiscountPerCurrency.compute(currency, (key, value) ->
                                value == null ? 0 : value + discount);
                    }
                }
                nextPageToken = response.nextPageToken();
            } catch (CostExplorerException e) {
                this.logger.error(e);
                throw new ToolCallException("AWS API error: " + e.getMessage());
            }
        } while (nextPageToken != null);

        discounts.sort((a, b) -> Double.compare(b.value(), a.value()));

        try {
            DiscountReport discountReport = new DiscountReport(discounts, totalDiscountPerCurrency);
            return discountReport.generate();
        } catch (CsvRequiredFieldEmptyException | CsvDataTypeMismatchException e) {
            this.logger.error(e);
            throw new ToolCallException("Could not create report!", e);
        }
    }
}
