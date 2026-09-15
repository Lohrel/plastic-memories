package dev.lohrel.plasticmemories.provider;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ProviderRequest {
    private final ProviderSettings settings;
    private final List<Message> messages;

    public ProviderRequest(ProviderSettings settings, List<Message> messages) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
    }

    public ProviderSettings settings() {
        return settings;
    }

    public List<Message> messages() {
        return messages;
    }

    public static final class Message {
        private final String role;
        private final String content;

        public Message(String role, String content) {
            this.role = Objects.requireNonNull(role, "role");
            this.content = Objects.requireNonNull(content, "content");
        }

        public String role() {
            return role;
        }

        public String content() {
            return content;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Message other)) return false;
            return role.equals(other.role) && content.equals(other.content);
        }

        @Override
        public int hashCode() {
            return 31 * role.hashCode() + content.hashCode();
        }

        @Override
        public String toString() {
            return "Message{role='" + role + "', content='" + content + "'}";
        }
    }
}
