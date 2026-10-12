package com.docchat.controller;

import com.docchat.dto.ChatRequest;
import com.docchat.dto.ChatResponse;
import com.docchat.dto.CitationDto;
import com.docchat.service.ChatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @Test
    @DisplayName("POST /api/chat with valid question should return answer with citations")
    void shouldAnswerQuestionSuccessfully() throws Exception {
        CitationDto citation = new CitationDto("doc_1", "handbook.pdf", 0, "Working hours are 9 AM to 5 PM.", 0.85);
        ChatResponse response = new ChatResponse(
                "What are working hours?",
                "According to handbook.pdf, working hours are 9 AM to 5 PM.",
                List.of(citation)
        );

        when(chatService.ask(any(ChatRequest.class))).thenReturn(response);

        String jsonPayload = """
                {
                    "question": "What are working hours?"
                }
                """;

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").isEqualTo("What are working hours?"))
                .andExpect(jsonPath("$.answer").contains("9 AM to 5 PM"))
                .andExpect(jsonPath("$.citations[0].documentName").isEqualTo("handbook.pdf"))
                .andExpect(jsonPath("$.citations[0].chunkIndex").isEqualTo(0));
    }

    @Test
    @DisplayName("POST /api/chat with blank question should return 400 Bad Request")
    void shouldRejectBlankQuestion() throws Exception {
        String jsonPayload = """
                {
                    "question": "   "
                }
                """;

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").isEqualTo(400))
                .andExpect(jsonPath("$.error").isEqualTo("Validation Failed"));
    }
}
