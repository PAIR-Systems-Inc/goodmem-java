# GoodMem Java SDK

Public source mirror for the official **`ai.pairsys:goodmem-java`** SDK.
GoodMem gives AI agents persistent, searchable memory through a separately
deployed memory and retrieval service.

- [Java SDK documentation](https://docs.goodmem.ai/docs/reference/sdk/java/)
- [Maven Central](https://central.sonatype.com/artifact/ai.pairsys/goodmem-java)
- [Javadoc](https://javadoc.io/doc/ai.pairsys/goodmem-java)
- [GoodMem installation](https://docs.goodmem.ai/docs/getting-started/)
- [LangChain4j integration](https://github.com/PAIR-Systems-Inc/goodmem-langchain4j)
- [Spring AI integration](https://github.com/PAIR-Systems-Inc/goodmem-spring-ai)

## Install

Requires Java 21 or later. Use the release shown in [VERSION](VERSION):

```xml
<dependency>
  <groupId>ai.pairsys</groupId>
  <artifactId>goodmem-java</artifactId>
  <version>0.2.12</version>
</dependency>
```

The SDK connects to a GoodMem server using its base URL and an API key.
See the documentation for setup and examples.

## Source provenance

This repository contains **only SDK sources and registry resources already
published in Maven Central's official sources JAR**, plus this mirror's
documentation and synchronization tooling. It contains no GoodMem server source,
private repository history, unpublished SDK code, or release credentials.

`src/main/java` contains the Java files; `src/main/resources` contains the registry
JSON files. Their contents are copied without modification. [provenance.json](provenance.json)
records the original public download URLs and SHA-256 digests. The original
published POM is preserved as [published-pom.xml](published-pom.xml); older releases
may still name the private development repository in that historical metadata.

This is a source-browsing mirror, not the SDK's development/build repository.
Install the published Maven artifact for application use. Issues can be reported
here; source fixes are made in the development repository and appear here after
publication.

## Synchronization

An hourly GitHub Actions workflow checks Maven Central for the latest stable
release, verifies the source archive's published checksum and POM identity/license,
and creates a source snapshot and `vVERSION` tag when a new release is detected.
It downloads only public Maven artifacts and uses this repository's own Actions
token for commits. It has no access to the private development repository.

The mirror starts with 0.2.2; older releases remain available on Maven Central.
It follows the latest release, so releases superseded between checks may not get
a mirror tag. For a specific published version, maintainers can run:

```sh
python3 scripts/sync_maven.py --version 0.2.2
```

## License

The SDK's published POM declares **Apache License 2.0**; see [LICENSE](LICENSE).
Mirror tooling and documentation are also Apache-2.0.
The separately deployed GoodMem server is governed by the
[GoodMem Free Binary License v1.0](https://goodmem.ai/license).
