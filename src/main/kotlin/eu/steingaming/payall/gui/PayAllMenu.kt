package eu.steingaming.payall.gui

import com.mojang.blaze3d.vertex.PoseStack
import eu.steingaming.payall.PayAll
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiComponent
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.FastColor
import kotlin.properties.Delegates

class PayAllMenu : Screen(Component.nullToEmpty("PayAll")) {
    private var startButton by Delegates.notNull<Button>()
    private var delay by Delegates.notNull<EditBox>()
    private var amount by Delegates.notNull<EditBox>()
    private var cmd by Delegates.notNull<EditBox>()
    private var dryRun by Delegates.notNull<Boolean>()

    private val stringList: MutableList<Triple<String, Int, Int>> = mutableListOf()

    override fun init() {
        var currentX = height / 3 + 9
        fun textToInput(text: String): EditBox {
            stringList += Triple(text, width / 3 + 3, currentX)
            return EditBox(
                Minecraft.getInstance().font,
                width / 3 + 200,
                currentX.also {
                    currentX += 25
                },
                80,
                20,
                Component.nullToEmpty("AWDA")
            ).also { addRenderableWidget(it) }
        }

        super.init()
        println(this.minecraft?.mouseHandler?.isMouseGrabbed)
        this.minecraft?.mouseHandler?.releaseMouse()

        startButton = this.addRenderableWidget(Button.builder(Component.nullToEmpty("Start/Stop")) {
            PayAll.instance.handle(
                delay.value.toDoubleOrNull() ?: return@builder,
                amount.value.toLongOrNull() ?: return@builder,
                cmd = cmd.value.split(" ").toTypedArray(),
                dryRun = dryRun
            )
        }.pos(width / 2 - 75, height - height / 3 - 24).build())
        delay = textToInput("Delay: ")
        amount = textToInput("Amount: ")
        cmd = textToInput("Custom Command (empty for default): ")
        dryRun = false
        addRenderableWidget(CycleButton.onOffBuilder(false).create(width / 3 + 4, currentX, 30, 50, Component.nullToEmpty("What")) { _, it ->
            dryRun = it
        })
    }

    override fun renderBackground(poseStack: PoseStack) {
        super.renderBackground(poseStack)
        GuiComponent.fill(poseStack,  // Size = 1920*1080; start = 1920/*
            width / 3, height / 3,                    // start   X, Y
            width - width / 3, height - height / 3,   // end     X, Y
            FastColor.ARGB32.color(100, 255, 255, 255)
        )
    }

    override fun render(poseStack: PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(poseStack)
        super.render(poseStack, mouseX, mouseY, partialTick)
        for ((text, x, y) in stringList)
            drawString(poseStack, Minecraft.getInstance().font, text, x, y, FastColor.ARGB32.color(78, 165, 40, 223))
    }

    override fun tick() {
        delay.tick()
    }
}