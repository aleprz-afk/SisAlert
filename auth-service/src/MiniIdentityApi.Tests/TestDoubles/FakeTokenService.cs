using MiniIdentityApi.Application.Interfaces;
using MiniIdentityApi.Domain.Entities;

namespace MiniIdentityApi.Tests.TestDoubles;

public class FakeTokenService : ITokenService
{
    public string GenerateToken(User user)
    {
        return $"fake-token-for-{user.Username}";
    }
}