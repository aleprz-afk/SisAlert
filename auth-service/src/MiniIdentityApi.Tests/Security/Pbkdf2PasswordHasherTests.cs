using MiniIdentityApi.Infrastructure.Security;
using Xunit;

namespace MiniIdentityApi.Tests.Security;

public class Pbkdf2PasswordHasherTests
{
    private readonly Pbkdf2PasswordHasher _hasher = new();

    [Fact]
    public void Verify_ShouldAcceptCorrectPassword_AndRejectWrongOne()
    {
        var salt = _hasher.GenerateSalt();
        var hash = _hasher.Hash("Pass123!", salt);

        Assert.StartsWith("pbkdf2-sha256$", hash);
        Assert.True(_hasher.Verify("Pass123!", salt, hash));
        Assert.False(_hasher.Verify("WrongPass", salt, hash));
    }

    [Fact]
    public void Verify_ShouldAcceptLegacySha256Hashes_AndAskForRehash()
    {
        var legacy = new Sha256PasswordHasher();
        var salt = legacy.GenerateSalt();
        var legacyHash = legacy.Hash("Pass123!", salt);

        Assert.True(_hasher.Verify("Pass123!", salt, legacyHash));
        Assert.False(_hasher.Verify("WrongPass", salt, legacyHash));
        Assert.True(_hasher.NeedsRehash(legacyHash));
    }

    [Fact]
    public void NeedsRehash_ShouldBeFalse_ForCurrentHashes_AndTrue_ForFewerIterations()
    {
        var salt = _hasher.GenerateSalt();
        Assert.False(_hasher.NeedsRehash(_hasher.Hash("Pass123!", salt)));
        Assert.True(_hasher.NeedsRehash("pbkdf2-sha256$1000$AAAA"));
    }

    [Fact]
    public void Verify_ShouldReturnFalse_ForMalformedHash()
    {
        Assert.False(_hasher.Verify("Pass123!", "sal", "pbkdf2-sha256$abc$no-es-base64"));
    }
}
