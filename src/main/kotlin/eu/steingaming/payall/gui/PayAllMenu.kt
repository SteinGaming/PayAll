package eu.steingaming.payall.gui

import com.mojang.blaze3d.vertex.PoseStack
import eu.steingaming.payall.PayAll
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.util.FastColor

class PayAllMenu : Screen(Component.nullToEmpty("PayAll")) {
    companion object {
        private var delay: EditBox? = null
        private var amount: EditBox? = null
        private var cmd: EditBox? = null
        private val stringList: MutableList<Triple<String, Int, Int>> = mutableListOf()
    }


    override fun init() {
        var currentX = height / 3 + 5
        fun textToInput(text: String, hint: String): EditBox {
            stringList += Triple(text, width / 3 + 3, currentX)
            return EditBox(
                Minecraft.getInstance().font,
                (width - width / 3) - (width / 8),
                currentX.also {
                    currentX += 25
                },
                width / 7,
                20,
                CommonComponents.EMPTY
            ).also { it.setHint(Component.literal("§7$hint")) }
        }
        fun EditBox?.construct(text: String, hint: String): EditBox {
            val box = this?.also { currentX += 25 } ?: textToInput(text, hint)
            addRenderableWidget(box)
            return box
        }
        this.minecraft?.mouseHandler?.releaseMouse()
        this.addRenderableWidget(Button.builder(Component.nullToEmpty("Start/Stop")) {
            PayAll.instance.handle(
                delay!!.value.toDoubleOrNull() ?: return@builder,
                amount!!.value.toLongOrNull() ?: return@builder,
                cmd = cmd!!.value.split(" ").toTypedArray(),
                dryRun = false
            )
        }.pos(width / 2 - 75, height - height / 3 - 24).build())
        this.addRenderableWidget(Button.builder(Component.nullToEmpty("Dryrun")) {
            PayAll.instance.handle(
                delay!!.value.toDoubleOrNull() ?: return@builder,
                amount!!.value.toLongOrNull() ?: return@builder,
                cmd = cmd!!.value.split(" ").toTypedArray(),
                dryRun = true
            )
        }.pos(width / 2 - 75, height - height / 3 - 44).build())
        delay =  delay.construct("Delay: ", "1.5 = 1500ms pause")
        amount = amount.construct("Amount: ", "10000000")
        cmd =    cmd.construct("Custom Command (empty for default): ", "pay ! $")
    }

    override fun renderBackground(poseStack: PoseStack) {
        //super.renderBackground(poseStack)
        //GuiComponent.fill(poseStack,  // Size = 1920*1080; start = 1920/*
        //    width / 3, height / 3,                    // start   X, Y
        //    width - width / 3, height - height / 3,   // end     X, Y
        //    FastColor.ARGB32.color(100, 255, 255, 255)
        //)
    }

    override fun render(poseStack: PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(poseStack)
        super.render(poseStack, mouseX, mouseY, partialTick)
        for ((text, x, y) in stringList)
            drawString(poseStack, Minecraft.getInstance().font, text, x, y, FastColor.ARGB32.color(100, 165, 40, 223))
    }

    override fun tick() {
        delay?.tick()
        amount?.tick()
        cmd?.tick()
    }
}