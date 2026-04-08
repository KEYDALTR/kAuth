package com.kauth.dialog;

import com.kauth.config.ConfigManager;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public class VerificationDialogFactory {

    private final Plugin plugin;
    private final ConfigManager config;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public VerificationDialogFactory(Plugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public Dialog build(Player player, String maskedEmail) {
        return build(player, maskedEmail, null);
    }

    public Dialog build(Player player, String maskedEmail, Component errorMessage) {
        List<DialogBody> bodyParts = new ArrayList<>();

        bodyParts.add(DialogBody.plainMessage(mm.deserialize("")));
        bodyParts.add(DialogBody.plainMessage(mm.deserialize(
                "<gradient:#00D4FF:#4AEAFF:#2E5CFF>KEYDAL NETWORK</gradient>"), 200));
        bodyParts.add(DialogBody.plainMessage(mm.deserialize("")));
        bodyParts.add(DialogBody.plainMessage(mm.deserialize(
                "<color:#B0C4D4>Doğrulama kodu <color:#4AEAFF>" + maskedEmail + "</color> adresine gönderildi.</color>"), 200));
        bodyParts.add(DialogBody.plainMessage(mm.deserialize(
                "<color:#B0C4D4>Lütfen kodu aşağıya giriniz.</color>"), 200));
        bodyParts.add(DialogBody.plainMessage(mm.deserialize("")));

        if (errorMessage != null) {
            bodyParts.add(DialogBody.plainMessage(errorMessage, 200));
            bodyParts.add(DialogBody.plainMessage(mm.deserialize("")));
        }

        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.text(
                "verification_code",
                mm.deserialize("<color:#4AEAFF>Doğrulama Kodu</color>")
        ).build());

        ActionButton submitButton = ActionButton.builder(mm.deserialize("<color:#4AEAFF>Doğrula</color>"))
                .action(DialogAction.customClick(
                        DialogCallbacks.verificationCallback(plugin, config, this, maskedEmail),
                        ClickCallback.Options.builder().build()
                ))
                .build();

        return Dialog.create(factory -> {
            var builder = factory.empty();
            builder.base(
                    DialogBase.builder(mm.deserialize(
                            "<color:#00D4FF>KEYDAL</color> <color:#3A4F6A>│</color> <color:#4AEAFF>E-Posta Doğrulama</color>"))
                            .body(bodyParts)
                            .inputs(inputs)
                            .canCloseWithEscape(false)
                            .afterAction(DialogBase.DialogAfterAction.CLOSE)
                            .build()
            );
            builder.type(DialogType.notice(submitButton));
        });
    }

    /**
     * E-posta adresini maskele: e***@g***.com
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@");
        String user = parts[0];
        String domain = parts[1];

        String maskedUser = user.charAt(0) + "***";
        int dotIdx = domain.lastIndexOf('.');
        String maskedDomain;
        if (dotIdx > 0) {
            maskedDomain = domain.charAt(0) + "***" + domain.substring(dotIdx);
        } else {
            maskedDomain = domain.charAt(0) + "***";
        }
        return maskedUser + "@" + maskedDomain;
    }
}
