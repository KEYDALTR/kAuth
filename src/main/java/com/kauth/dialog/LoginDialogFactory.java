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

public class LoginDialogFactory {

    private final Plugin plugin;
    private final ConfigManager config;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public LoginDialogFactory(Plugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public Dialog build(Player player) {
        return build(player, null);
    }

    public Dialog build(Player player, Component errorMessage) {
        List<DialogBody> bodyParts = new ArrayList<>();

        for (String line : config.loginBodyRaw()) {
            String processed = line;
            if (player != null) {
                processed = processed.replace("%player_name%", player.getName());
            }
            bodyParts.add(DialogBody.plainMessage(mm.deserialize(processed), config.loginWidth()));
        }

        if (errorMessage != null) {
            bodyParts.add(DialogBody.plainMessage(Component.empty()));
            bodyParts.add(DialogBody.plainMessage(errorMessage, config.loginWidth()));
        }

        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.text(
                "password",
                mm.deserialize(config.msg("login.button_password"))
        ).build());

        ActionButton submitButton = ActionButton.builder(mm.deserialize(config.loginSubmitLabel()))
                .action(DialogAction.customClick(
                        DialogCallbacks.loginCallback(plugin, config, this),
                        ClickCallback.Options.builder().build()
                ))
                .build();

        ActionButton cancelButton = ActionButton.builder(mm.deserialize(config.loginCancelLabel()))
                .action(DialogAction.customClick(
                        DialogCallbacks.loginCancelCallback(config),
                        ClickCallback.Options.builder().build()
                ))
                .build();

        return Dialog.create(factory -> {
            var builder = factory.empty();
            builder.base(
                    DialogBase.builder(config.loginTitle(player))
                            .body(bodyParts)
                            .inputs(inputs)
                            .canCloseWithEscape(config.canCloseWithEscape())
                            .afterAction(DialogBase.DialogAfterAction.CLOSE)
                            .build()
            );
            builder.type(DialogType.confirmation(submitButton, cancelButton));
        });
    }
}
