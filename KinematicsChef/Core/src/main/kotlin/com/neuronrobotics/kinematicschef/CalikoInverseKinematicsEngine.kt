/*
 * Copyright 2018 Ryan Benasutti
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.neuronrobotics.kinematicschef

import au.edu.federation.caliko.FabrikBone3D
import au.edu.federation.caliko.FabrikChain3D
import au.edu.federation.caliko.FabrikJoint3D
import au.edu.federation.utils.Vec3f
import com.neuronrobotics.kinematicschef.dhparam.DhParam
import com.neuronrobotics.kinematicschef.dhparam.toDhParams
import com.neuronrobotics.kinematicschef.dhparam.toFrameTransformation
import com.neuronrobotics.kinematicschef.util.getPointMatrix
import com.neuronrobotics.kinematicschef.util.getTranslation
import com.neuronrobotics.kinematicschef.util.length
import com.neuronrobotics.sdk.addons.kinematics.DHChain
import com.neuronrobotics.sdk.addons.kinematics.DhInverseSolver
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR
import kotlin.math.acos

/**
 * A [DhInverseSolver] which uses Caliko's iterative solver.
 */
internal class CalikoInverseKinematicsEngine : DhInverseSolver {

    /**
     * Calculate the joint angles for the system.
     *
     * @param target The target frame transformation.
     * @param jointSpaceVector The current joint angles.
     * @param chain The DH params for the system.
     * @return The joint angles necessary to meet the target.
     */
    override fun inverseKinematics(
        target: TransformNR,
        jointSpaceVector: DoubleArray,
        chain: DHChain
    ): DoubleArray = inverseKinematicsWithError(target, jointSpaceVector, chain).first

    /**
     * Calculate the joint angles for the system.
     *
     * @param target The target frame transformation.
     * @param jointSpaceVector The current joint angles.
     * @param chain The DH params for the system.
     * @return The joint angles necessary to meet the target and the solve error.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun inverseKinematicsWithError(
        target: TransformNR,
        @Suppress("UNUSED_PARAMETER") jointSpaceVector: DoubleArray,
        chain: DHChain
    ): Pair<DoubleArray, Float> {
        require(jointSpaceVector.size == chain.links.size) {
            "The joint angles and DH params must have equal size."
        }

        val fabrikChain = FabrikChain3D()
        fabrikChain.setFixedBaseMode(true)

        val dhParams = chain.toDhParams()
        dhParams.forEachIndexed { index, dhParam ->
            // TODO: Make this get the actual bone length
            val boneLength: Float =
                if (index == dhParams.size - 1) {
                    defaultBoneLength
                } else {
                    calculateLinkLength(dhParam)
                }

            if (index == 0) {
                // The first link can't be added using addConsecutiveBone()
                fabrikChain.addBone(
                    FabrikBone3D(
                        Vec3f(0.0f),
                        FORWARD_AXIS.times(boneLength)
                    )
                )

                fabrikChain.setFreelyRotatingGlobalHingedBasebone(UP_AXIS)
            } else {
                // TODO: The directionUV could be X or Z depending on if we need to use d or r
                // TODO: No way to set hinge rotation limits from DH params alone
                fabrikChain.addConsecutiveFreelyRotatingHingedBone(
                    FORWARD_AXIS,
                    boneLength,
                    FabrikJoint3D.JointType.LOCAL_HINGE,
                    UP_AXIS
                )
            }
        }

        val solveError = fabrikChain.solveForTarget(target.x, target.y, target.z)

        // Add a unit vector pointing up as the first element so we can get the angle of the first
        // link
        val directions = fabrikChain.chain.map { it.directionUV }.toMutableList()
        directions.add(0, baseUnitVector)

        val angles = mutableListOf<Double>()
        for (i in 0 until directions.size - 1) {
            val vec1 = directions[i]
            val vec2 = directions[i + 1]
            angles.add(vec2.angle(vec1))
        }

        return angles.map {
            if (!it.isFinite()) {
                0.0
            } else {
                it
            }
        }.toDoubleArray() to solveError
    }

    /**
     * Calculates the length of a link from its [DhParam].
     */
    private fun calculateLinkLength(dhParam: DhParam): Float {
        val pointBeforeTransform = getPointMatrix(0, 0, 0)
        val pointAfterTransform = pointBeforeTransform.mult(dhParam.toFrameTransformation())

        val possibleLength = pointAfterTransform.getTranslation()
            .minus(pointBeforeTransform.getTranslation())
            .length()
            .toFloat()

        return if (possibleLength == 0.0f) {
            defaultBoneLength
        } else {
            possibleLength
        }
    }

    companion object {
        private const val defaultBoneLength = 10.0f
        private val baseUnitVector = Vec3f(0.0f, 0.0f, 1.0f).normalise()
        private val UP_AXIS = Vec3f(0.0f, 0.0f, 1.0f)
        private val FORWARD_AXIS = Vec3f(1.0f, 0.0f, 0.0f)
        private val RIGHT_AXIS = Vec3f(0.0f, 1.0f, 0.0f)
    }
}

private fun Vec3f.angle(vec: Vec3f): Double = acos(dot(vec) / (length() * vec.length()))

private fun Vec3f.dot(vec: Vec3f): Double = (x * vec.x + y * vec.y + z * vec.z).toDouble()

private fun FabrikChain3D.solveForTarget(x: Number, y: Number, z: Number) =
    solveForTarget(x.toFloat(), y.toFloat(), z.toFloat())