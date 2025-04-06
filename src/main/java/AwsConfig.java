import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.CostExplorerClientBuilder;

@ApplicationScoped
public class AwsConfig {
    @Produces
    CostExplorerClient costExplorerClient() {
        CostExplorerClientBuilder costExplorerClientBuilder = CostExplorerClient.builder();
        return costExplorerClientBuilder.build();
    }
}
