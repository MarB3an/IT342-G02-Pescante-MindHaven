package edu.cit.pescante.mindhaven.dashboard;

public record UpdateStaffStatusRequest(
        Boolean active,
        Boolean onCall
) {
}
