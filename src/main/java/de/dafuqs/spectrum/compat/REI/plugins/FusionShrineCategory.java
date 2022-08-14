package de.dafuqs.spectrum.compat.REI.plugins;

import com.google.common.collect.Lists;
import de.dafuqs.spectrum.compat.REI.SpectrumPlugins;
import de.dafuqs.spectrum.registries.SpectrumBlocks;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.fluid.Fluids;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FusionShrineCategory implements DisplayCategory<FusionShrineDisplay> {
	
	private static final EntryIngredient FUSION_SHRINE_BASALT = EntryIngredients.of(SpectrumBlocks.FUSION_SHRINE_BASALT);
	
	@Override
	public CategoryIdentifier getCategoryIdentifier() {
		return SpectrumPlugins.FUSION_SHRINE;
	}
	
	@Override
	public Text getTitle() {
		return Text.translatable("block.spectrum.fusion_shrine");
	}
	
	@Override
	public Renderer getIcon() {
		return EntryStacks.of(SpectrumBlocks.FUSION_SHRINE_BASALT);
	}
	
	@Override
	public List<Widget> setupDisplay(@NotNull FusionShrineDisplay display, @NotNull Rectangle bounds) {
		Point startPoint = new Point(bounds.getCenterX() - 58, bounds.getCenterY() - 43);
		List<Widget> widgets = Lists.newArrayList();
		
		widgets.add(Widgets.createRecipeBase(bounds));
		
		if (!display.isUnlocked()) {
			widgets.add(Widgets.createLabel(new Point(startPoint.x - 6, startPoint.y + 33), Text.translatable("container.spectrum.rei.pedestal_crafting.recipe_not_unlocked_line_1")).leftAligned().color(0x3f3f3f).noShadow());
			widgets.add(Widgets.createLabel(new Point(startPoint.x - 6, startPoint.y + 43), Text.translatable("container.spectrum.rei.pedestal_crafting.recipe_not_unlocked_line_2")).leftAligned().color(0x3f3f3f).noShadow());
		} else {
			List<EntryIngredient> output = display.getOutputEntries();
			
			// input slots
			int ingredientSize = display.craftingInputs.size();
			int startX = Math.max(-10, 10 - ingredientSize * 10);
			for (int i = 0; i < ingredientSize; i++) {
				EntryIngredient currentIngredient = display.craftingInputs.get(i);
				widgets.add(Widgets.createSlot(new Point(startPoint.x + startX + i * 20, startPoint.y + 9)).markInput().entries(currentIngredient));
			}
			
			// shrine + fluid
			if (!display.fluidInput.equals(EntryIngredients.of(Fluids.EMPTY))) {
				widgets.add(Widgets.createSlot(new Point(startPoint.x + 10, startPoint.y + 35)).entries(FUSION_SHRINE_BASALT).disableBackground());
				widgets.add(Widgets.createSlot(new Point(startPoint.x + 30, startPoint.y + 35)).markInput().entries(display.fluidInput));
			} else {
				widgets.add(Widgets.createSlot(new Point(startPoint.x + 20, startPoint.y + 35)).entries(FUSION_SHRINE_BASALT).disableBackground());
			}
			
			// output arrow and slot
			widgets.add(Widgets.createArrow(new Point(startPoint.x + 60, startPoint.y + 35)));
			widgets.add(Widgets.createResultSlotBackground(new Point(startPoint.x + 95, startPoint.y + 35)));
			widgets.add(Widgets.createSlot(new Point(startPoint.x + 95, startPoint.y + 35)).markOutput().disableBackground().entries(output.get(0)));
			
			if (display.getDescription().isPresent()) {
				Text description = (Text) display.getDescription().get();
				widgets.add(Widgets.createLabel(new Point(startPoint.x - 10, startPoint.y + 65), description).leftAligned().color(0x3f3f3f).noShadow());
			}
			
			// description text
			// special handling for "1 second". Looks nicer
			Text text;
			if (display.craftingTime == 20) {
				text = Text.translatable("container.spectrum.rei.pedestal_crafting.crafting_time_one_second_and_xp", 1, display.experience);
			} else {
				text = Text.translatable("container.spectrum.rei.pedestal_crafting.crafting_time_and_xp", (display.craftingTime / 20), display.experience);
			}
			widgets.add(Widgets.createLabel(new Point(startPoint.x - 10, startPoint.y + 75), text).leftAligned().color(0x3f3f3f).noShadow());
			
			
		}
		return widgets;
	}
	
	@Override
	public int getDisplayHeight() {
		return 100;
	}
	
}
