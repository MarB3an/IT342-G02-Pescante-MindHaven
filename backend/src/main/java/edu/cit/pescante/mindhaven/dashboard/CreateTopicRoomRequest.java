package edu.cit.pescante.mindhaven.dashboard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTopicRoomRequest(
        @NotBlank(message = "Topic room name is required.")
        @Size(min = 3, max = 100, message = "Room name must be between 3 and 100 characters.")
        String roomName,

        @NotBlank(message = "Description is required.")
        @Size(min = 10, max = 500, message = "Description must be between 10 and 500 characters.")
        String description
) {
}
