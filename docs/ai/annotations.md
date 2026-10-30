# Annotations and framework placement

The module boundary decides where an annotation belongs, not a generic Spring service template.
See [architecture](layered-architecture.md) before introducing a new bean.

| Location | Existing convention |
|----------|---------------------|
| `model` | Plain records/classes with invariants; no framework or Lombok annotations |
| `application/service` | Plain Java classes marked with the project's `@ApplicationService`, implementing input ports |
| `application/port` | Framework-free interfaces, commands and exceptions |
| `adapter/in/rest` | `@RestController`, mapping annotations, Jakarta request validation, request/web model records |
| `adapter/in/scheduling` | Spring scheduling at the inbound boundary |
| `adapter/out` | Spring-managed port implementations; JPA, Jena and other integration details |
| `bootstrap` | `@Configuration`, bean wiring, security and frontend serving |

`ApplicationServiceConfig` scans the custom marker annotation. Do not replace it with `@Service`
or add Spring transaction annotations to the application ring. Production dependencies use
constructors; avoid field injection and unnecessary `@Autowired` on a sole constructor.

The persistence adapter deliberately has both a port implementation such as
`@Repository JpaInstanceRepository` and a Spring Data `InstanceJpaRepository`. Do not remove the
adapter because Spring can generate the latter. Keep JPA and Lombok confined to entities; avoid
introducing `@Data`, `@Slf4j` or `@RequiredArgsConstructor` across the codebase.

Place Spring transactions at a framework boundary where needed; existing modifying persistence
queries use `@Transactional`. Preserve their semantics rather than imposing class-level
transactions on every service. Hibernate validates the Flyway schema; it does not own it.

## Spring Boot 4 test and configuration imports

Use the imports already present in neighbouring classes. Frequently moved packages include:

```text
org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
org.springframework.boot.persistence.autoconfigure.EntityScan
org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
org.springframework.boot.test.web.server.LocalServerPort
```

Use `@MockitoBean` for Spring test replacements, not the older `@MockBean`.
Use Testcontainers 2's `org.testcontainers.postgresql.PostgreSQLContainer`.
Keep test-only Spring annotations out of production code and out of the inner rings.
