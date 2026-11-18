package com.digination.urlshortener.link;

import java.net.URI;
import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.validation.Valid;

@RestController
public class LinkController {

    private final LinkService service;
    private final LinkProperties props;

    public LinkController(LinkService service, LinkProperties props) {
        this.service = service;
        this.props = props;
    }

    @PostMapping("/api/links")
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody LinkRequest request, Principal me) {
        String longUrl = request.url().trim();
        LinkResponse body = toResponse(service.shorten(longUrl, me.getName()), longUrl);
        return ResponseEntity.created(URI.create(body.shortUrl())).body(body);
    }

    @GetMapping("/api/links")
    public List<LinkResponse> list(Principal me) {
        return service.list(me.getName()).stream().map(l -> toResponse(l.code(), l.url())).toList();
    }

    @GetMapping("/api/links/{code}")
    public LinkResponse get(@PathVariable String code, Principal me) {
        return toResponse(code, service.get(code, me.getName()).orElseThrow(() -> notFound(code)));
    }

    @DeleteMapping("/api/links/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code, Principal me) {
        if (!service.delete(code, me.getName())) {
            throw notFound(code);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{shortCode:[0-9A-Za-z]{6}}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(find(shortCode)))
                .build();
    }

    private String find(String code) {
        return service.resolve(code).orElseThrow(() -> notFound(code));
    }

    private static ResponseStatusException notFound(String code) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Short code '" + code + "' not found");
    }

    private LinkResponse toResponse(String code, String longUrl) {
        return new LinkResponse(code, baseUrl() + "/" + code, longUrl);
    }

    private String baseUrl() {
        if (StringUtils.hasText(props.baseUrl())) {
            return props.baseUrl().replaceAll("/+$", "");
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
