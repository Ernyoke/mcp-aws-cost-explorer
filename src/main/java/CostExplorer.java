import data.Discount;
import data.DiscountReport;
import data.Usage;
import data.UsageReport;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.*;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        List<Usage> usages = new ArrayList<>();
        Map<String, Double> totalCostPerCurrency = new HashMap<>();

        String nextPageToken = null;

        do {
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
                    .nextPageToken(nextPageToken)
                    .build();

            // Get the cost data
            GetCostAndUsageResponse response = costExplorerClient.getCostAndUsage(request);
            for (ResultByTime result : response.resultsByTime()) {
                for (Group group : result.groups()) {
                    double cost = Double.parseDouble(group.metrics().get("UnblendedCost").amount());

                    String serviceName = group.keys().getFirst();
                    String operation = group.keys().getLast();
                    String currency = group.metrics().get("UnblendedCost").unit();
                    usages.add(new Usage.Builder()
                            .serviceName(serviceName)
                            .operation(operation)
                            .currency(currency)
                            .value(cost)
                            .build());

                    totalCostPerCurrency.compute(currency, (key, value) -> value == null ? 0 : value + cost);
                }
            }

            nextPageToken = response.nextPageToken();
        } while (nextPageToken != null);

        usages.sort((a, b) -> Double.compare(b.value(), a.value()));

        UsageReport usageReport = new UsageReport(usages, totalCostPerCurrency);

        try {
            return usageReport.build();
        } catch (Exception e) {
            throw new ToolCallException("Could not create report!", e);
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

            // Get the cost data
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
        } while (nextPageToken != null);

        discounts.sort((a, b) -> Double.compare(b.value(), a.value()));

        DiscountReport discountReport = new DiscountReport(discounts, totalDiscountPerCurrency);

        try {
            return discountReport.build();
        } catch (Exception e) {
            throw new ToolCallException("Could not create report!", e);
        }
    }
}
