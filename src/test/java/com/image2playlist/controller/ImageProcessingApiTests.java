package com.image2playlist.controller;

import com.image2playlist.ocr.OcrService;
import com.image2playlist.provider.MusicProviderType;
import com.image2playlist.provider.MusicTrackMatch;
import com.image2playlist.provider.TrackSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ImageProcessingApiTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OcrService ocrService;

    @MockitoBean
    private TrackSearchService trackSearchService;

    @Test
    void uploadProcessAndSelectMatch() throws Exception {
        when(ocrService.extractText(any(Path.class)))
                .thenReturn("Blinding Lights\nThe Weeknd\n\nStarboy\nThe Weeknd");
        when(trackSearchService.search(any(), eq(MusicProviderType.APPLE_MUSIC), anyString(), nullable(String.class)))
                .thenReturn(List.of(new MusicTrackMatch(
                        MusicProviderType.APPLE_MUSIC, "apple-track-1", "Blinding Lights",
                        "The Weeknd", "After Hours", null, "https://music.apple.com/song/1", 98.5
                )));

        String uploadJson = mockMvc.perform(multipart("/api/images/upload")
                        .file(new MockMultipartFile(
                                "image", "screen.png", "image/png",
                                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0}
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageId").exists())
                .andReturn().getResponse().getContentAsString();
        String imageId = com.jayway.jsonpath.JsonPath.read(uploadJson, "$.imageId");

        String sessionJson = mockMvc.perform(post("/api/images/{imageId}/process", imageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.songs.length()").value(2))
                .andExpect(jsonPath("$.songs[0].title").value("Blinding Lights"))
                .andExpect(jsonPath("$.songs[0].matches[0].title").value("Blinding Lights"))
                .andReturn().getResponse().getContentAsString();
        String sessionId = com.jayway.jsonpath.JsonPath.read(sessionJson, "$.sessionId");
        String candidateId = com.jayway.jsonpath.JsonPath.read(sessionJson, "$.songs[0].candidateId");
        String matchId = com.jayway.jsonpath.JsonPath.read(sessionJson, "$.songs[0].matches[0].matchId");

        mockMvc.perform(put("/api/sessions/{sessionId}/songs/{candidateId}", sessionId, candidateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Blinding Lights","artist":"The Weeknd","selectedMatchId":"%s"}
                                """.formatted(matchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.songs[0].status").value("MATCHED"))
                .andExpect(jsonPath("$.songs[0].matches[0].selected").value(true));
    }
}
