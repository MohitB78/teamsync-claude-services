package com.teamsync.notification.dto;

import com.teamsync.notification.model.NotificationPreference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for notification preferences.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferenceDTO {

    private String id;
    private boolean emailEnabled;
    private boolean pushEnabled;
    private boolean inAppEnabled;
    private boolean mentionsOnly;
    private DigestSettingsDTO digestSettings;
    private QuietHoursDTO quietHours;
    private Map<String, TypePreferenceDTO> typePreferences;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DigestSettingsDTO {
        private boolean enabled;
        private String frequency;
        private String sendTime;
        private String timezone;
        private boolean lowPriorityToDigest;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuietHoursDTO {
        private boolean enabled;
        private String startTime;
        private String endTime;
        private String timezone;
        private boolean allowUrgent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypePreferenceDTO {
        private boolean enabled;
        private boolean email;
        private boolean push;
        private boolean inApp;
    }

    /**
     * Convert entity to DTO.
     */
    public static NotificationPreferenceDTO fromEntity(NotificationPreference entity) {
        if (entity == null) return null;

        DigestSettingsDTO digestDTO = null;
        if (entity.getDigestSettings() != null) {
            var d = entity.getDigestSettings();
            digestDTO = DigestSettingsDTO.builder()
                    .enabled(d.getEnabled())
                    .frequency(d.getFrequency() != null ? d.getFrequency().name() : "DAILY")
                    .sendTime(d.getSendTime())
                    .timezone(d.getTimezone())
                    .lowPriorityToDigest(d.getLowPriorityToDigest())
                    .build();
        }

        QuietHoursDTO quietDTO = null;
        if (entity.getQuietHours() != null) {
            var q = entity.getQuietHours();
            quietDTO = QuietHoursDTO.builder()
                    .enabled(q.getEnabled())
                    .startTime(q.getStartTime())
                    .endTime(q.getEndTime())
                    .timezone(q.getTimezone())
                    .allowUrgent(q.getAllowUrgent())
                    .build();
        }

        java.util.Map<String, TypePreferenceDTO> typePrefs = null;
        if (entity.getTypePreferences() != null) {
            typePrefs = new java.util.HashMap<>();
            for (var entry : entity.getTypePreferences().entrySet()) {
                var tp = entry.getValue();
                typePrefs.put(entry.getKey(), TypePreferenceDTO.builder()
                        .enabled(tp.isEnabled())
                        .email(tp.isEmail())
                        .push(tp.isPush())
                        .inApp(tp.isInApp())
                        .build());
            }
        }

        return NotificationPreferenceDTO.builder()
                .id(entity.getId())
                .emailEnabled(Boolean.TRUE.equals(entity.getEmailEnabled()))
                .pushEnabled(Boolean.TRUE.equals(entity.getPushEnabled()))
                .inAppEnabled(Boolean.TRUE.equals(entity.getInAppEnabled()))
                .mentionsOnly(Boolean.TRUE.equals(entity.getMentionsOnly()))
                .digestSettings(digestDTO)
                .quietHours(quietDTO)
                .typePreferences(typePrefs)
                .build();
    }
}
