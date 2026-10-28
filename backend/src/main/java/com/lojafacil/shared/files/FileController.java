package com.lojafacil.shared.files;

import java.io.IOException;
import java.time.Duration;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Arquivos")
public class FileController {
    private final FileStorageService storage;

    public FileController(FileStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/files/{storeId:[0-9]+}/{name:[a-f0-9-]{36}\\.(?:jpg|png)}")
    public ResponseEntity<InputStreamResource> get(@PathVariable String storeId, @PathVariable String name) throws IOException {
        String path = storeId + "/" + name;
        if (!storage.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        MediaType type = name.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(storage.open(path)));
    }
}
