/*
 * This file is part of kinematics-chef.
 *
 * kinematics-chef is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * kinematics-chef is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with kinematics-chef.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.neuronrobotics.kinematicschef

import arrow.core.Either
import com.neuronrobotics.kinematicschef.classifier.ChainIdentifier
import com.neuronrobotics.kinematicschef.classifier.ClassifierError
import com.neuronrobotics.kinematicschef.classifier.DhClassifier
import com.neuronrobotics.kinematicschef.dhparam.DhChainElement
import com.neuronrobotics.kinematicschef.dhparam.RevoluteJoint
import com.neuronrobotics.kinematicschef.dhparam.SphericalWrist
import com.neuronrobotics.kinematicschef.dhparam.toDHLinks
import com.neuronrobotics.kinematicschef.dhparam.toDhParams
import com.neuronrobotics.kinematicschef.util.immutableListOf
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class InverseKinematicsEngineTest {

    @Test
    @Disabled
    fun `test for error when validating euler angles`() {
        val chain = TestUtil.makeMockChain(TestUtil.randomDhParamList(6).toDHLinks())

        // Use random dh params because we don't care about their values
        val wrist1 = SphericalWrist(TestUtil.randomDhParamList(3))
        val wrist2 = SphericalWrist(TestUtil.randomDhParamList(3))
        val mockChainIdentifier = mock<ChainIdentifier> {
            on { identifyChain(chain.toDhParams()) } doReturn
                immutableListOf<DhChainElement>(wrist1, wrist2)
        }

        val mockDhClassifier = mock<DhClassifier> {
            on { deriveEulerAngles(wrist1) } doReturn Either.left(
                ClassifierError("Wrist 1 invalid.")
            )
            on { deriveEulerAngles(wrist2) } doReturn Either.left(
                ClassifierError("Wrist 2 invalid.")
            )
        }

        val engine = InverseKinematicsEngine(mockChainIdentifier, mockDhClassifier)

        assertThrows<NotImplementedError> {
            engine.inverseKinematics(
                TransformNR(),
                listOf(0.0, 0.0, 0.0).toDoubleArray(),
                chain
            )
        }
    }

    @Test
    @Disabled
    fun `test 6DOF inverse kinematics`() {
        val chain = TestUtil.makeMockChain(ArrayList(TestUtil.cmmInputArmDhParams.toDHLinks()))

        val dhParams = chain.toDhParams()

        val mockChainIdentifier = mock<ChainIdentifier> {
            on { identifyChain(dhParams) } doReturn immutableListOf(
                RevoluteJoint(dhParams.subList(0, 1)),
                RevoluteJoint(dhParams.subList(1, 2)),
                RevoluteJoint(dhParams.subList(2, 3)),
                SphericalWrist(dhParams.subList(3, 6))
            )
        }

        val mockDhClassifier = mock<DhClassifier> {
        }

        val ikEngine = InverseKinematicsEngine(
            mockChainIdentifier,
            mockDhClassifier
        )
    }
}
