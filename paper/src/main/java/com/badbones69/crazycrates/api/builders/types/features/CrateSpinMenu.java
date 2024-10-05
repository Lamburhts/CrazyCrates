package com.badbones69.crazycrates.api.builders.types.features;

import com.badbones69.crazycrates.api.builders.gui.StaticInventoryBuilder;
import com.badbones69.crazycrates.api.enums.Messages;
import com.badbones69.crazycrates.api.objects.Crate;
import com.badbones69.crazycrates.api.objects.gui.GuiSettings;
import com.ryderbelserion.vital.paper.api.builders.gui.interfaces.Gui;
import com.ryderbelserion.vital.paper.api.builders.gui.interfaces.GuiFiller;
import com.ryderbelserion.vital.paper.api.builders.gui.interfaces.GuiItem;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class CrateSpinMenu extends StaticInventoryBuilder {

    private final GuiSettings settings;

    public CrateSpinMenu(final Player player, final GuiSettings settings) {
        super(player, settings.getCrate(), settings.getTitle(), settings.getRows());

        this.settings = settings;
    }

    private final Player player = getPlayer();
    private final Crate crate = getCrate();
    private final Gui gui = getGui();

    @Override
    public void open() {
        if (this.crate == null) return;

        if (this.settings.isFillerToggled()) {
            final GuiItem item = this.settings.getFillerStack();

            final GuiFiller guiFiller = this.gui.getFiller();

            switch (this.settings.getFillerType()) {
                case FILL -> guiFiller.fill(item);

                case FILL_BORDER -> guiFiller.fillBorder(item);

                case FILL_TOP -> guiFiller.fillTop(item);

                case FILL_SIDE -> guiFiller.fillSide(GuiFiller.Side.BOTH, List.of(item));

                case FILL_BOTTOM -> guiFiller.fillBottom(item);
            }
        }

        final UUID uuid = player.getUniqueId();
        final String fileName = this.crate.getFileName();

        this.settings.getButtons().forEach((slot, button) -> this.gui.setItem(slot, button.getGuiItem()));

        this.gui.setOpenGuiAction(action -> {
            this.userManager.addRespinPrize(uuid, fileName, this.settings.getPrize().getSectionName());
        });

        this.gui.setCloseGuiAction(action -> {
            if (!this.userManager.hasUser(uuid) && this.userManager.hasRespinPrize(uuid, fileName)) { // if they aren't in the cache, then we run this.
                Messages.crate_prize_respin_not_claimed.sendMessage(player, new HashMap<>() {{
                    put("{crate_pretty}", crate.getCrateName());
                    put("{crate}", fileName);
                    put("{prize}", userManager.getRespinPrize(uuid, fileName));
                }});
            }

            this.crateManager.removePlayerFromOpeningList(this.player);
            this.crateManager.removeCrateInUse(this.player);
            this.crateManager.removeCrateTask(this.player);
            this.crateManager.endCrate(this.player);
        });

        this.gui.setItem(this.settings.getSlot(), new GuiItem(this.settings.getPrize().getDisplayItem(this.player, this.crate)));

        this.gui.open(this.player);
    }
}