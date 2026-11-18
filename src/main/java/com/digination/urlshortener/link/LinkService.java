package com.digination.urlshortener.link;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class LinkService {

    private static final Logger log = LoggerFactory.getLogger(LinkService.class);

    private final JdbcClient db;
    private final ShortCodeGenerator generator;
    private final LinkProperties props;

    public LinkService(JdbcClient db, ShortCodeGenerator generator, LinkProperties props) {
        this.db = db;
        this.generator = generator;
        this.props = props;
    }

    public String shorten(String longUrl, String owner) {
        String url = validate(longUrl);

        for (int attempt = 1; attempt <= props.maxGenerationAttempts(); attempt++) {
            String code = generator.generate(props.codeLength());
            int inserted = db.sql("insert into links (code, url, owner) values (?, ?, ?) on conflict do nothing")
                    .params(code, url, owner).update();
            if (inserted == 1) {
                log.debug("Stored short code {} after {} attempt(s)", code, attempt);
                return code;
            }
            log.warn("Short code collision on {} (attempt {})", code, attempt);
        }
        throw new ShortCodeGenerationException(
                "Could not generate a unique short code after " + props.maxGenerationAttempts() + " attempts");
    }

    @Cacheable(value = "links", unless = "#result == null")
    public Optional<String> resolve(String code) {
        return db.sql("select url from links where code = ?").param(code).query(String.class).optional();
    }

    public List<Link> list(String owner) {
        return db.sql("select code, url from links where owner = ? order by code limit 100")
                .param(owner).query(Link.class).list();
    }

    public Optional<String> get(String code, String owner) {
        return db.sql("select url from links where code = ? and owner = ?").params(code, owner)
                .query(String.class).optional();
    }

    @CacheEvict(value = "links", key = "#code")
    public boolean delete(String code, String owner) {
        return db.sql("delete from links where code = ? and owner = ?").params(code, owner).update() == 1;
    }

    private static String validate(String raw) {
        String candidate = raw == null ? "" : raw.trim();
        try {
            URI uri = new URI(candidate);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new InvalidUrlException("URL must use the http or https scheme");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new InvalidUrlException("URL must include a host");
            }
            return candidate;
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("Malformed URL: " + e.getReason());
        }
    }
}
