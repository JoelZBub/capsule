package capsule.recipes;

import capsule.helpers.MinecraftNBT;
import capsule.items.CapsuleItem;
import capsule.items.CapsuleItems;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.crafting.IRecipeFactory;
import net.minecraftforge.common.crafting.JsonContext;

import java.util.ArrayList;

public class DyeCapsuleRecipeFactory implements IRecipeFactory {

    @Override
    public IRecipe parse(JsonContext context, JsonObject json) {
        return new DyeCapsuleRecipe();
    }

    public class DyeCapsuleRecipe extends net.minecraftforge.registries.IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {

        /**
         * Used to check if a recipe matches current crafting inventory
         */
        public boolean matches(CraftingInventory inv, World worldIn) {
            ItemStack itemstack = ItemStack.EMPTY;
            ArrayList<ItemStack> arraylist = Lists.<ItemStack>newArrayList();

            for (int i = 0; i < inv.getSizeInventory(); ++i) {
                ItemStack itemstack1 = inv.getStackInSlot(i);

                if (!itemstack1.isEmpty()) {
                    if (itemstack1.getItem() instanceof CapsuleItem) {
                        itemstack = itemstack1;
                    } else {
                        if (itemstack1.getItem() != Items.DYE) {
                            return false;
                        }

                        arraylist.add(itemstack1);
                    }
                }
            }

            return !itemstack.isEmpty() && !arraylist.isEmpty();
        }

        /**
         * Returns an Item that is the result of this recipe
         */
        public ItemStack getCraftingResult(CraftingInventory invCrafting) {
            ItemStack itemstack = ItemStack.EMPTY;
            int[] aint = new int[3];
            int i = 0;
            int j = 0;
            CapsuleItem item = null;

            for (int k = 0; k < invCrafting.getSizeInventory(); ++k) {
                ItemStack itemstack1 = invCrafting.getStackInSlot(k);

                if (!itemstack1.isEmpty()) {
                    if (itemstack1.getItem() instanceof CapsuleItem) {
                        item = (CapsuleItem) itemstack1.getItem();
                        itemstack = itemstack1.copy();

                        if (MinecraftNBT.hasColor(itemstack1)) {
                            int l = MinecraftNBT.getColor(itemstack);
                            float f = (float) (l >> 16 & 255) / 255.0F;
                            float f1 = (float) (l >> 8 & 255) / 255.0F;
                            float f2 = (float) (l & 255) / 255.0F;
                            i = (int) ((float) i + Math.max(f, Math.max(f1, f2)) * 255.0F);
                            aint[0] = (int) ((float) aint[0] + f * 255.0F);
                            aint[1] = (int) ((float) aint[1] + f1 * 255.0F);
                            aint[2] = (int) ((float) aint[2] + f2 * 255.0F);
                            ++j;
                        }
                    } else {
                        if (!net.minecraftforge.oredict.DyeUtils.isDye(itemstack1)) {
                            return ItemStack.EMPTY;
                        }

                        float[] afloat = net.minecraftforge.oredict.DyeUtils.colorFromStack(itemstack1).get().getColorComponentValues();
                        int l1 = (int) (afloat[0] * 255.0F);
                        int i2 = (int) (afloat[1] * 255.0F);
                        int j2 = (int) (afloat[2] * 255.0F);
                        i += Math.max(l1, Math.max(i2, j2));
                        aint[0] += l1;
                        aint[1] += i2;
                        aint[2] += j2;
                        ++j;
                    }
                }
            }

            if (item == null) {
                return ItemStack.EMPTY;
            } else {
                int i1 = aint[0] / j;
                int j1 = aint[1] / j;
                int k1 = aint[2] / j;
                float f3 = (float) i / (float) j;
                float f4 = (float) Math.max(i1, Math.max(j1, k1));
                i1 = (int) ((float) i1 * f3 / f4);
                j1 = (int) ((float) j1 * f3 / f4);
                k1 = (int) ((float) k1 * f3 / f4);
                int lvt_12_3_ = (i1 << 8) + j1;
                lvt_12_3_ = (lvt_12_3_ << 8) + k1;
                MinecraftNBT.setColor(itemstack, lvt_12_3_);
                return itemstack;
            }
        }

        @Override
        public boolean canFit(int width, int height) {
            return width * height >= 2;
        }

        /**
         * Returns the size of the recipe area
         */
        public int getRecipeSize() {
            return 10;
        }

        public ItemStack getRecipeOutput() {
            return new ItemStack(CapsuleItems.capsule, 1, CapsuleItem.STATE_EMPTY);
        }

        public NonNullList<ItemStack> getRemainingItems(CraftingInventory inv) {
            NonNullList<ItemStack> nonnulllist = NonNullList.<ItemStack>withSize(inv.getSizeInventory(), ItemStack.EMPTY);

            for (int i = 0; i < nonnulllist.size(); ++i) {
                ItemStack itemstack = inv.getStackInSlot(i);
                nonnulllist.set(i, net.minecraftforge.common.ForgeHooks.getContainerItem(itemstack));
            }

            return nonnulllist;
        }

        public boolean isDynamic() {
            return true;
        }
    }
}