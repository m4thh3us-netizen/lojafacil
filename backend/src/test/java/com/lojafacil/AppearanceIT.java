package com.lojafacil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.lojafacil.auth.Role;
import com.lojafacil.support.IntegrationTest;
import com.lojafacil.support.TestData;

@Import(TestData.class)
class AppearanceIT extends IntegrationTest {
    @Autowired
    MockMvc mvc;

    @Test
    void fotografiaEhOtimizadaESoValeDepoisDeSalvar() throws Exception {
        TestData.StoreCtx s = data.newStore();
        var owner = user(TestData.principal(s.owner()));

        String body = mvc.perform(multipart("/api/admin/store/appearance/photo").file(photo(3000, 2000)).with(owner).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String path = body.replaceAll(".*\"path\":\"([^\"]+)\".*", "$1");

        BufferedImage stored = ImageIO.read(Path.of("target/test-uploads", path).toFile());
        assertThat(Math.max(stored.getWidth(), stored.getHeight())).isEqualTo(2400);
        assertThat(Files.size(Path.of("target/test-uploads", path))).isLessThan(2_000_000L);

        mvc.perform(get("/api/admin/store/appearance").with(owner)).andExpect(jsonPath("$.mode").value("COLOR"));

        mvc.perform(put("/api/admin/store/appearance").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("PHOTO", path, 40, 80)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("PHOTO"))
                .andExpect(jsonPath("$.imageUrl").value("/files/" + path));

        mvc.perform(post("/api/admin/store/appearance/reset").with(owner).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("COLOR")).andExpect(jsonPath("$.glass").value(74));
    }

    @Test
    void limitesDeLegibilidadeSaoValidadosNoServidor() throws Exception {
        TestData.StoreCtx s = data.newStore();
        var owner = user(TestData.principal(s.owner()));
        mvc.perform(put("/api/admin/store/appearance").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("COLOR", null, 90, 74))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/store/appearance").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("COLOR", null, 20, 30))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/store/appearance").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("PHOTO", null, 20, 74))).andExpect(status().isBadRequest());
    }

    @Test
    void naoAceitaFotoDeOutraLojaNemOperador() throws Exception {
        TestData.StoreCtx a = data.newStore();
        TestData.StoreCtx b = data.newStore();
        String body = mvc.perform(multipart("/api/admin/store/appearance/photo").file(photo(1200, 800))
                        .with(user(TestData.principal(a.owner()))).with(csrf()))
                .andReturn().getResponse().getContentAsString();
        String pathOfA = body.replaceAll(".*\"path\":\"([^\"]+)\".*", "$1");

        mvc.perform(put("/api/admin/store/appearance").with(user(TestData.principal(b.owner()))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json("PHOTO", pathOfA, 20, 74)))
                .andExpect(status().isBadRequest());

        var operator = user(TestData.principal(data.member(a.storeId(), Role.OPERATOR)));
        mvc.perform(get("/api/admin/store/appearance").with(operator)).andExpect(status().isOk());
        mvc.perform(put("/api/admin/store/appearance").with(operator).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("COLOR", null, 20, 74))).andExpect(status().isForbidden());
    }

    private static String json(String mode, String path, int dim, int glass) {
        return """
                {"mode":"%s","imagePath":%s,"color":"#334155","posX":50,"posY":40,"dim":%d,"glass":%d}
                """.formatted(mode, path == null ? "null" : "\"" + path + "\"", dim, glass);
    }

    private static MockMultipartFile photo(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Random r = new Random(1);
        for (int i = 0; i < 400; i++) {
            g.setColor(new Color(r.nextInt(256), r.nextInt(256), r.nextInt(256)));
            g.fillRect(r.nextInt(w), r.nextInt(h), 40 + r.nextInt(300), 40 + r.nextInt(300));
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return new MockMultipartFile("file", "sala.png", "image/png", out.toByteArray());
    }
}
