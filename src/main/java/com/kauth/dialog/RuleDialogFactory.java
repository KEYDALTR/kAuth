package com.kauth.dialog;

import com.kauth.config.ConfigManager;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public class RuleDialogFactory {

    private final Plugin plugin;
    private final ConfigManager config;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public RuleDialogFactory(Plugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public Dialog build(Player player) {
        List<DialogBody> bodyParts = new ArrayList<>();

        for (String line : config.ruleBodyRaw()) {
            String processed = line;
            if (player != null) {
                processed = processed.replace("%player_name%", player.getName());
            }
            bodyParts.add(DialogBody.plainMessage(mm.deserialize(processed)));
        }

        List<DialogInput> inputs = new ArrayList<>();
        if (config.ruleAgreeEnabled()) {
            inputs.add(DialogInput.bool(
                    config.ruleAgreeKey(),
                    mm.deserialize(config.ruleAgreeLabel())
            ).initial(false).build());
        }

        ActionButton confirmButton = ActionButton.builder(mm.deserialize(config.ruleConfirmLabel()))
                .action(DialogAction.customClick(
                        DialogCallbacks.ruleConfirmCallback(plugin, config),
                        ClickCallback.Options.builder().build()
                ))
                .build();

        return Dialog.create(factory -> {
            var builder = factory.empty();
            builder.base(
                    DialogBase.builder(config.ruleTitle())
                            .body(bodyParts)
                            .inputs(inputs)
                            .canCloseWithEscape(false)
                            .afterAction(DialogBase.DialogAfterAction.CLOSE)
                            .build()
            );
            builder.type(DialogType.notice(confirmButton));
        });
    }
}
