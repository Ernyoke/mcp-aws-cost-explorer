import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.*;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class CostExplorer {
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

    private final DecimalFormat decimalFormat = new DecimalFormat("#.###########");

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
        if (!Utils.isValidDate(startDate)) {
            throw new ToolCallException("Invalid start date: " + startDate);
        }

        if (!Utils.isValidDate(endDate)) {
            throw new ToolCallException("Invalid start date: " + startDate);
        }

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


        // Create the request
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
                .build();

        // Get the cost data
        GetCostAndUsageResponse response = costExplorerClient.getCostAndUsage(request);

        record Usage(String service, String operation, Double value, String currency) {
        }

        List<Usage> usages = new ArrayList<>();

        for (ResultByTime result : response.resultsByTime()) {
            for (Group group : result.groups()) {
                double cost = Double.parseDouble(group.metrics().get("UnblendedCost").amount());

                String serviceName = group.keys().getFirst();
                String operation = group.keys().getLast();
                String currency = group.metrics().get("UnblendedCost").unit();
                usages.add(new Usage(serviceName, operation, cost, currency));
            }
        }

        usages.sort((a, b) -> Double.compare(b.value, a.value));

        StringBuilder result = new StringBuilder();
        result.append("Service,").append("Operation,").append("Currency,").append("Value,").append("\n");

        for (Usage usage : usages) {
            result.append(usage.service()).append(",")
                    .append(usage.operation()).append(",")
                    .append(usage.currency()).append(",")
                    .append(decimalFormat.format(usage.value())).append(",\n")
                    .append(usage.value()).append(",\n");
        }

        return result.toString();
    }

    @Tool(description = "Return all the discounts by services and operations in all the regions for a given period.")
    public String getDiscounts(
            @ToolArg(description = "Start date in format of yyyy-MM-dd") String startDate,
            @ToolArg(description = "End date in format of yyyy-MM-dd") String endDate,
            @ToolArg(description = "AWS regions where the discounts should be retrieved. In case we want to get the discounts for resources in all regions, we should provide an empty list.") List<String> regions
    ) {
        if (!Utils.isValidDate(startDate)) {
            throw new ToolCallException("Invalid start date: " + startDate);
        }

        if (!Utils.isValidDate(endDate)) {
            throw new ToolCallException("Invalid start date: " + startDate);
        }

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


        // Create the request
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
                .build();

        // Get the cost data
        GetCostAndUsageResponse response = costExplorerClient.getCostAndUsage(request);

        record Discount(String service, String type, Double value, String currency) {
        }

        List<Discount> discounts = new ArrayList<>();

        for (ResultByTime result : response.resultsByTime()) {
            for (Group group : result.groups()) {
                double cost = Double.parseDouble(group.metrics().get("UnblendedCost").amount());

                String serviceName = group.keys().getFirst();
                String operation = group.keys().getLast();
                String currency = group.metrics().get("UnblendedCost").unit();
                discounts.add(new Discount(serviceName, operation, cost, currency));
            }
        }

        discounts.sort((a, b) -> Double.compare(b.value, a.value));

        StringBuilder result = new StringBuilder();
        result.append("Service,").append("Discount Type,").append("Currency,").append("Value,").append("\n");

        for (Discount discount : discounts) {
            result.append(discount.service()).append(",")
                    .append(discount.type()).append(",")
                    .append(discount.currency()).append(",")
                    .append(decimalFormat.format(discount.value())).append(",\n")
                    .append(discount.value()).append(",\n");
        }

        return result.toString();
    }
}
