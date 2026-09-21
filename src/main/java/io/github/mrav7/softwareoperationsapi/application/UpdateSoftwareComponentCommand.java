package io.github.mrav7.softwareoperationsapi.application;

public record UpdateSoftwareComponentCommand(
        boolean namePresent,
        String name,
        boolean descriptionPresent,
        String description) {

    public UpdateSoftwareComponentCommand {
        if (!namePresent && !descriptionPresent) {
            throw new InvalidDomainInputException("At least one component field must be provided");
        }
    }
}
