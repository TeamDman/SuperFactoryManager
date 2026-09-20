package ca.teamdman.sfm.client.program;

import net.minecraft.resources.ResourceLocation;

import java.io.IOException;

/** Local user-owned trust; a program can neither create nor change this authority. */
public interface ClientProgramSignerAuthority {
    boolean permits(ClientProgramIdentity identity, ResourceLocation capability);
    void revokeAll() throws IOException;
    void revokeAtLocation(ClientProgramIdentity identity) throws IOException;

    ClientProgramSignerAuthority NONE = new ClientProgramSignerAuthority() {
        public boolean permits(ClientProgramIdentity identity, ResourceLocation capability) { return false; }
        public void revokeAll() { }
        public void revokeAtLocation(ClientProgramIdentity identity) { }
    };
}
