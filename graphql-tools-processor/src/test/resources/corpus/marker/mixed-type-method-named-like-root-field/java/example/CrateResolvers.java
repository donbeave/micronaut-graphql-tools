package example;

import jakarta.inject.Singleton;

@Singleton
public class CrateResolvers implements CrateQueryContract, CrateTagContract {

    @Override
    public Crate crate() {
        return new Crate();
    }

    @Override
    public String tag(Crate crate) {
        return "t";
    }

}
