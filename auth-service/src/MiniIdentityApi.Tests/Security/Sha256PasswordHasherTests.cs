using MiniIdentityApi.Infrastructure.Security;
using Xunit;

namespace MiniIdentityApi.Tests.Security;

public class Sha256PasswordHasherTests
{
    [Fact]
    public void Verify_ShouldReturnTrue_WhenPasswordMatchesHash()
    {
        // Arrange
        var hasher = new Sha256PasswordHasher();
        var password = "Pass123!";
        var salt = hasher.GenerateSalt();
        var hash = hasher.Hash(password, salt);

        // Act
        var result = hasher.Verify(password, salt, hash);

        // Assert
        Assert.True(result);
    }

    [Fact]
    public void Verify_ShouldReturnFalse_WhenPasswordDoesNotMatchHash()
    {
        // Arrange
        var hasher = new Sha256PasswordHasher();
        var salt = hasher.GenerateSalt();
        var hash = hasher.Hash("Pass123!", salt);

        // Act
        var result = hasher.Verify("WrongPass", salt, hash);

        // Assert
        Assert.False(result);
    }
}