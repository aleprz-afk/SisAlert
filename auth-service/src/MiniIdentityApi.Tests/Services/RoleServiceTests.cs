using MiniIdentityApi.Application.Services;
using MiniIdentityApi.Infrastructure.Repositories;
using Xunit;

namespace MiniIdentityApi.Tests.Services;

public class RoleServiceTests
{
    [Fact]
    public void Create_ShouldAddRole_WhenNameIsValid()
    {
        // Arrange
        var roleRepository = new InMemoryRoleRepository();
        var service = new RoleService(roleRepository);

        // Act
        service.Create("Admin");

        // Assert
        var role = roleRepository.FindByName("Admin");
        Assert.NotNull(role);
        Assert.Equal("Admin", role!.Name);
    }

    [Fact]
    public void Create_ShouldThrowInvalidOperationException_WhenRoleAlreadyExists()
    {
        // Arrange
        var roleRepository = new InMemoryRoleRepository();
        var service = new RoleService(roleRepository);

        service.Create("Admin");

        // Act + Assert
        Assert.Throws<InvalidOperationException>(() => service.Create("Admin"));
    }

    [Fact]
    public void AddPermission_ShouldAddPermissionToRole()
    {
        // Arrange
        var roleRepository = new InMemoryRoleRepository();
        var service = new RoleService(roleRepository);

        service.Create("Admin");

        // Act
        service.AddPermission("Admin", "users.read", "Can read users");

        // Assert
        var role = roleRepository.FindByName("Admin");
        Assert.NotNull(role);
        Assert.Single(role!.Permissions);
        Assert.Equal("users.read", role.Permissions[0].Code);
    }
}