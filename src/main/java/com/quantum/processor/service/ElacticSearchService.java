
@Service
public class ElasticIndexService {

    @Value("${POD_NAME:local}")
    private String podName;

    @Value("${POD_NAMESPACE:local}")
    private String namespace;

    private final ElasticsearchClient client;

    public void indexChunkEvent(ChunkResult result) {

        result.setPodName(podName);
        result.setNamespace(namespace);
        result.setTimestamp(Instant.now());

        client.index(i -> i
            .index("quantum-chunk-events")
            .document(result)
        );
    }
}