package org.mind.framework;

import org.junit.runner.RunWith;
import org.mind.framework.annotation.Mapping;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * @author Marcus
 * @version 1.0
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"file:web/WEB-INF/springContext.xml", "file:web/WEB-INF/businessConfig.xml"})
public class FrameWorkTest {

    @Mapping(method = {RequestMethod.POST, RequestMethod.GET})
    public void to() {

    }

    @Mapping("/dadf")
    public void from() {

    }
}
