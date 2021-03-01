/*
 * Copyright 2015-2021 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.artio.engine.framer;

import uk.co.real_logic.artio.engine.ConnectedSessionInfo;

import java.util.*;

import static uk.co.real_logic.artio.engine.FixEngine.ENGINE_LIBRARY_ID;

class EngineLibraryInfo implements LibraryInfo
{
    private final ArrayList<ConnectedSessionInfo> sessions;
    private final GatewaySessions gatewaySessions;

    EngineLibraryInfo(final GatewaySessions gatewaySessions)
    {
        sessions = new ArrayList<>((List<ConnectedSessionInfo>)(List<?>)gatewaySessions.sessions()); // TODO
        this.gatewaySessions = gatewaySessions;
    }

    public int libraryId()
    {
        return ENGINE_LIBRARY_ID;
    }

    public String libraryName()
    {
        return "Gateway Library";
    }

    public List<ConnectedSessionInfo> sessions()
    {
        return sessions;
    }

    public String toString()
    {
        return "EngineLibraryInfo{" +
            "sessions=" + sessions +
            '}';
    }

    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final EngineLibraryInfo that = (EngineLibraryInfo)o;

        return Objects.equals(gatewaySessions, that.gatewaySessions);
    }

    public int hashCode()
    {
        return gatewaySessions != null ? gatewaySessions.hashCode() : 0;
    }
}
