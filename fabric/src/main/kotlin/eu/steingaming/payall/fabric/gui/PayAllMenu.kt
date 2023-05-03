package eu.steingaming.payall.fabric.gui

import eu.steingaming.payall.fabric.PayAllFabric
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.EditBoxWidget
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.debug.DebugRenderer.drawString
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text

class PayAllMenu : Screen(Text.of("PayAll")) {
    companion object {
        private var delay: EditBoxWidget? = null
        private var amount: EditBoxWidget? = null
        private var cmd: EditBoxWidget? = null
        private val stringList: MutableList<Triple<String, Int, Int>> = mutableListOf()
    }


    override fun init() {
        var currentX = height / 3 + 5
        fun textToInput(text: String, hint: String): EditBoxWidget {
            stringList += Triple(text, width / 3 + 3, currentX)
            return EditBoxWidget(
                MinecraftClient.getInstance().textRenderer,
                (width - width / 3) - (width / 8),
                currentX.also {
                    currentX += 25
                },
                width / 7,
                20,
                Text.empty(),
                Text.of("§7$hint")
            ).apply { setTooltip(Tooltip.of(Text.of("§7$hint"))) }
        }
        fun EditBoxWidget?.construct(text: String, hint: String): EditBoxWidget {
            val box = this?.also { currentX += 25 } ?: textToInput(text, hint)
            addDrawableChild(box)
            return box
        }
        MinecraftClient.getInstance().mouse.unlockCursor()
        addDrawableChild(ButtonWidget.builder(Text.of("Start/Stop")) {
            PayAllFabric.instance.handle(
                delay!!.text.toDoubleOrNull() ?: return@builder,
                amount!!.text.toLongOrNull() ?: return@builder,
                cmd = cmd!!.text.split(" ").toTypedArray(),
                dryRun = false
            )
        }.position(width / 2 - 75, height - height / 3 - 24).build())
        addDrawableChild(ButtonWidget.builder(Text.of("Dryrun")) {
            PayAllFabric.instance.handle(
                delay!!.text.toDoubleOrNull() ?: return@builder,
                amount!!.text.toLongOrNull() ?: return@builder,
                cmd = cmd!!.text.split(" ").toTypedArray(),
                dryRun = true
            )
        }.position(width / 2 - 75, height - height / 3 - 44).build())
        delay =  delay.construct("Delay: ", "1.5 = 1500ms pause")
        amount = amount.construct("Amount: ", "10000000")
        cmd =    cmd.construct("Custom Command (empty for default): ", "pay ! $")
    }

    override fun renderBackground(poseStack: MatrixStack) {
        //super.renderBackground(poseStack)
        //GuiComponent.fill(poseStack,  // Size = 1920*1080; start = 1920/*
        //    width / 3, height / 3,                    // start   X, Y
        //    width - width / 3, height - height / 3,   // end     X, Y
        //    FastColor.ARGB32.color(100, 255, 255, 255)
        //)
    }

    override fun render(poseStack: MatrixStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(poseStack)
        super.render(poseStack, mouseX, mouseY, partialTick)
        for ((text, x, y) in stringList)
            drawTextWithShadow(poseStack, MinecraftClient.getInstance().textRenderer, Text.of(text), x, y, 0xa528df)
    }

    override fun tick() {
        delay?.tick()
        amount?.tick()
        cmd?.tick()
    }
}