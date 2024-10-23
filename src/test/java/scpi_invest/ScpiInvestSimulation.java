package scpi_invest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.FeederBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.http;


public class ScpiInvestSimulation extends Simulation {

    private static final String KEYCLOAK_URL = "https://keycloak.check-consulting.net/realms/master/protocol/openid-connect/token";
    private static final String CLIENT_ID = "scpi-invest";
    private static final String USERNAME = System.getenv("userName");
    ;
    private static final String PASSWORD = System.getenv("password");

    int nbUsers = Integer.valueOf(System.getenv("nbUsers"));
    int duration = Integer.valueOf(System.getenv("duration"));
    String environment = "prd".equals(System.getenv("environment")) ? "" : System.getenv("environment")+".";

    public ScpiInvestSimulation() throws IOException {
    }

    String accessToken = getKeycloakAccessToken();

    FeederBuilder<String> feeder = csv("search.csv").circular();

    HttpProtocolBuilder httpProtocol = http.baseUrl("https://"+ environment +"scpi-invest-api.check-consulting.net");

    ChainBuilder getAllScpi = repeat(1).on(exec(http("Récupération de toutes les SCPI").get("/api/v1/scpi")
            .header("Authorization", "Bearer " + accessToken)
    ));

    ChainBuilder getScpiById = repeat(1).on(exec(http("Récupération d'une SCPI par ID").get("/api/v1/scpi/1")
            .header("Authorization", "Bearer " + accessToken)
    ));

    String requestBodyTemplate = "{\n" +
            "  \"searchTerm\": \"${searchTerm}\",\n" +
            "  \"localizations\": [\"${localizations}\"],\n" +
            "  \"sectors\": [\"${sectors}\"],\n" +
            "  \"amount\": ${amount},\n" +
            "  \"fees\": true\n" +
            "}";


    ChainBuilder filterByCrietria = repeat(1).on(exec(http("Recherche multi Critères").post("/api/v1/scpi/search")
            .header("Authorization", "Bearer " + accessToken)
            .body(StringBody(requestBodyTemplate))
            .asJson()
    ));


    ChainBuilder getInvestismentPerformances = repeat(1).on(exec(http("Portfolio des performances des investissement")
            .get("/api/v1/investement/portfolio/performance")
            .header("Authorization", "Bearer " + accessToken)
    ));


    ScenarioBuilder scenario = scenario("Tests de performances sur la récupération des SCPI")
            .feed(feeder)
            .exec(getAllScpi)
            .exec(getInvestismentPerformances)
            .exec(getScpiById)
            .exec(filterByCrietria);

    {
        setUp(scenario.injectOpen(rampUsers(nbUsers).during(duration)).protocols(httpProtocol));
    }


    public static String getKeycloakAccessToken() throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            HttpPost httpPost = new HttpPost(KEYCLOAK_URL);


            String body = "client_id=" + CLIENT_ID +
                    "&grant_type=password" +
                    "&username=" + USERNAME +
                    "&password=" + PASSWORD +
                    "&client_secret=";


            StringEntity entity = new StringEntity(body);
            httpPost.setEntity(entity);
            httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");


            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity());

                ObjectMapper mapper = new ObjectMapper();
                JsonNode jsonNode = mapper.readTree(responseBody);
                return jsonNode.get("access_token").asText();
            }
        }
    }


}
