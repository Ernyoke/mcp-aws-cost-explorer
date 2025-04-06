import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class CostExplorerTest {

    @Inject
    CostExplorer costExplorer;

    @Test
    void getCostsForTheLastMonth() {
        String res = costExplorer.getCostPerServiceAndOperation("2025-01-01",
                "2025-02-01", List.of("us-east-1"));
        System.out.println(res);
    }

    @Test
    void getDiscounts() {
        String res = costExplorer.getDiscounts("2025-01-01",
                "2025-02-01", List.of("us-east-1"));
        System.out.println(res);
    }
}