import data.DiscountReport;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

@QuarkusTest
class CostExplorerTest {

    @Inject
    CostExplorer costExplorer;

    @Test
    void getCostsForTheLastMonth() {
        String report = costExplorer.getCostPerServiceAndOperation("2025-01-01",
                "2025-02-01", List.of("us-east-1"));
        System.out.println(report);
    }

    @Test
    void getCostsPerLinkedAccountForTheLastMonth() {
        String report = costExplorer.getCostPerLinkedAccount("2025-01-01",
                "2025-02-01", List.of("us-east-1"));
        System.out.println(report);
    }

    @Test
    void getDiscounts() {
        String report = costExplorer.getDiscounts("2025-01-01",
                "2025-02-01", List.of("us-east-1"));
        System.out.println(report);
    }
}
